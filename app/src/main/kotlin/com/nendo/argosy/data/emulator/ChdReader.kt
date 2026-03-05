package com.nendo.argosy.data.emulator

import com.nendo.argosy.util.Logger
import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

object ChdReader {
    private const val TAG = "ChdReader"
    private const val CHD_MAGIC = "MComprHD"
    private const val V5_HEADER_SIZE = 124

    private const val COMP_TYPE_0 = 0
    private const val COMP_TYPE_1 = 1
    private const val COMP_TYPE_2 = 2
    private const val COMP_TYPE_3 = 3
    private const val COMP_NONE = 4
    private const val COMP_SELF = 5
    private const val COMP_PARENT = 6
    private const val COMP_RLE_SMALL = 7
    private const val COMP_RLE_LARGE = 8
    private const val COMP_SELF_0 = 9
    private const val COMP_SELF_1 = 10
    private const val COMP_PARENT_SELF = 11
    private const val COMP_PARENT_0 = 12
    private const val COMP_PARENT_1 = 13

    private const val CODEC_ZLIB = 0x7A6C6962
    private const val CODEC_LZMA = 0x6C7A6D61

    private data class ChdHeader(
        val hunkBytes: Int,
        val unitBytes: Int,
        val mapOffset: Long,
        val codecs: IntArray,
        val logicalSize: Long,
        val totalHunks: Int
    )

    private data class MapEntry(
        val compression: Int,
        val compLength: Int,
        val offset: Long
    )

    fun extractPS2Serial(file: File): String? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = readHeader(raf) ?: return null
                val entries = parseMap(raf, header) ?: return null

                val neededBytes = 101 * 2048
                val neededHunks = (neededBytes + header.hunkBytes - 1) / header.hunkBytes
                val hunksToRead = minOf(neededHunks, entries.size)

                val data = decompressHunks(raf, header, entries, hunksToRead) ?: return null
                findBootSerial(data)
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to extract PS2 serial from CHD | file=${file.name}", e)
            null
        }
    }

    private fun readHeader(raf: RandomAccessFile): ChdHeader? {
        if (raf.length() < V5_HEADER_SIZE) return null

        raf.seek(0)
        val bytes = ByteArray(V5_HEADER_SIZE)
        raf.readFully(bytes)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        val magic = ByteArray(8)
        buf.get(magic)
        if (String(magic, Charsets.US_ASCII) != CHD_MAGIC) return null

        buf.int // headerSize
        val version = buf.int
        if (version != 5) {
            Logger.debug(TAG, "Unsupported CHD version: $version")
            return null
        }

        val codecs = IntArray(4) { buf.int }
        val logicalSize = buf.long
        val mapOffset = buf.long
        buf.long // metadataOffset
        val hunkBytes = buf.int
        buf.int // unitBytes

        val totalHunks = ((logicalSize + hunkBytes - 1) / hunkBytes).toInt()
        return ChdHeader(hunkBytes, hunkBytes, mapOffset, codecs, logicalSize, totalHunks)
    }

    private fun parseMap(raf: RandomAccessFile, header: ChdHeader): List<MapEntry>? {
        raf.seek(header.mapOffset)

        val mapHeader = ByteArray(16)
        raf.readFully(mapHeader)
        val mh = ByteBuffer.wrap(mapHeader).order(ByteOrder.BIG_ENDIAN)

        val mapBytes = mh.int
        val firstOffs = read48(mh)
        mh.short // mapCrc
        val lengthBits = mh.get().toInt() and 0xFF
        val selfBits = mh.get().toInt() and 0xFF
        val parentBits = mh.get().toInt() and 0xFF

        val compressedSize = mapBytes - 16
        if (compressedSize <= 0) return null

        val compressedData = ByteArray(compressedSize)
        raf.readFully(compressedData)

        val decompressed = rawInflate(compressedData) ?: return null
        val reader = BitReader(decompressed)

        val huffman = HuffmanDecoder(16, 8)
        huffman.importTreeRle(reader)

        val compTypes = IntArray(header.totalHunks)
        var ci = 0
        var lastComp = 0
        while (ci < header.totalHunks) {
            val comp = huffman.decode(reader)
            when (comp) {
                COMP_RLE_SMALL -> {
                    val count = 3 + huffman.decode(reader)
                    repeat(count) { if (ci < header.totalHunks) compTypes[ci++] = lastComp }
                }
                COMP_RLE_LARGE -> {
                    val first = huffman.decode(reader)
                    val second = huffman.decode(reader)
                    val count = 19 + (first shl 4) + second
                    repeat(count) { if (ci < header.totalHunks) compTypes[ci++] = lastComp }
                }
                else -> {
                    compTypes[ci++] = comp
                    lastComp = comp
                }
            }
        }

        val neededBytes = 101 * 2048
        val maxEntries = minOf(
            (neededBytes + header.hunkBytes - 1) / header.hunkBytes,
            header.totalHunks
        )

        val entries = mutableListOf<MapEntry>()
        var curOffset = firstOffs
        var lastSelf = 0L

        for (hunkNum in 0 until maxEntries) {
            val rawType = compTypes[hunkNum]
            val resolvedType: Int
            var offset = curOffset
            var length = 0

            when (rawType) {
                COMP_TYPE_0, COMP_TYPE_1, COMP_TYPE_2, COMP_TYPE_3 -> {
                    resolvedType = rawType
                    length = reader.read(lengthBits).toInt()
                    reader.read(16) // CRC
                    curOffset += length
                }
                COMP_NONE -> {
                    resolvedType = COMP_NONE
                    length = header.hunkBytes
                    reader.read(16) // CRC
                    curOffset += length
                }
                COMP_SELF -> {
                    resolvedType = COMP_SELF
                    offset = reader.read(selfBits)
                    lastSelf = offset
                }
                COMP_SELF_0 -> {
                    resolvedType = COMP_SELF
                    offset = curOffset - header.hunkBytes
                    lastSelf = offset
                }
                COMP_SELF_1 -> {
                    resolvedType = COMP_SELF
                    offset = lastSelf + header.hunkBytes
                    lastSelf = offset
                }
                COMP_PARENT, COMP_PARENT_SELF, COMP_PARENT_0, COMP_PARENT_1 -> {
                    resolvedType = COMP_PARENT
                    if (rawType == COMP_PARENT) reader.read(parentBits)
                    offset = 0
                }
                else -> {
                    resolvedType = rawType
                }
            }

            entries.add(MapEntry(resolvedType, length, offset))
        }

        return entries
    }

    private fun decompressHunks(
        raf: RandomAccessFile,
        header: ChdHeader,
        entries: List<MapEntry>,
        count: Int
    ): ByteArray? {
        val output = ByteArrayOutputStream(count * header.hunkBytes)
        val decompressedCache = mutableMapOf<Long, ByteArray>()

        for (i in 0 until count) {
            val entry = entries[i]
            val decompressed = when (entry.compression) {
                COMP_TYPE_0, COMP_TYPE_1, COMP_TYPE_2, COMP_TYPE_3 -> {
                    val codecIndex = entry.compression
                    val codec = header.codecs[codecIndex]
                    val compressed = ByteArray(entry.compLength)
                    raf.seek(entry.offset)
                    raf.readFully(compressed)
                    decompressedCache[entry.offset] = compressed
                    decompressWithCodec(compressed, header.hunkBytes, codec)
                }
                COMP_NONE -> {
                    val raw = ByteArray(header.hunkBytes)
                    raf.seek(entry.offset)
                    raf.readFully(raw)
                    raw
                }
                COMP_SELF -> {
                    val cachedCompressed = decompressedCache[entry.offset]
                    if (cachedCompressed != null) {
                        val refEntry = entries.firstOrNull { it.offset == entry.offset && it.compression in 0..3 }
                        if (refEntry != null) {
                            decompressWithCodec(cachedCompressed, header.hunkBytes, header.codecs[refEntry.compression])
                        } else null
                    } else null
                }
                else -> null
            }

            if (decompressed != null && decompressed.size == header.hunkBytes) {
                output.write(decompressed)
            } else {
                output.write(ByteArray(header.hunkBytes))
            }
        }

        return output.toByteArray()
    }

    private fun decompressWithCodec(data: ByteArray, uncompressedSize: Int, codec: Int): ByteArray? {
        return when (codec) {
            CODEC_ZLIB -> rawInflate(data, uncompressedSize)
            CODEC_LZMA -> lzmaDecompress(data, uncompressedSize)
            else -> {
                Logger.debug(TAG, "Unsupported codec: 0x${codec.toString(16)}")
                null
            }
        }
    }

    private fun rawInflate(data: ByteArray, expectedSize: Int = 0): ByteArray? {
        return try {
            val inflater = Inflater(true)
            inflater.setInput(data)
            val outSize = if (expectedSize > 0) expectedSize else maxOf(data.size * 4, 65536)
            val output = ByteArrayOutputStream(outSize)
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && inflater.needsInput()) break
                output.write(buf, 0, n)
            }
            inflater.end()
            output.toByteArray()
        } catch (e: Exception) {
            Logger.debug(TAG, "rawInflate failed: ${e.message}")
            null
        }
    }

    private fun lzmaDecompress(data: ByteArray, uncompressedSize: Int): ByteArray? {
        return try {
            val stream = LZMAInputStream(
                ByteArrayInputStream(data),
                uncompressedSize.toLong(),
                3, 0, 2,
                uncompressedSize,
                null
            )
            val result = ByteArray(uncompressedSize)
            var read = 0
            while (read < uncompressedSize) {
                val n = stream.read(result, read, uncompressedSize - read)
                if (n < 0) break
                read += n
            }
            stream.close()
            if (read == uncompressedSize) result else null
        } catch (e: Exception) {
            Logger.debug(TAG, "LZMA decompression failed: ${e.message}")
            null
        }
    }

    fun extractPSXSerial(file: File): String? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = readHeader(raf) ?: return null
                val entries = parseMap(raf, header) ?: return null

                val neededBytes = 101 * 2048
                val neededHunks = (neededBytes + header.hunkBytes - 1) / header.hunkBytes
                val hunksToRead = minOf(neededHunks, entries.size)

                val data = decompressHunks(raf, header, entries, hunksToRead) ?: return null
                findPSXBootSerial(data)
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to extract PSX serial from CHD | file=${file.name}", e)
            null
        }
    }

    private fun findBootSerial(data: ByteArray): String? {
        val text = String(data, Charsets.ISO_8859_1)
        val pattern = Regex("""BOOT2\s*=\s*cdrom0:\\([A-Z]{4})_(\d{3})\.(\d{2});""")
        val match = pattern.find(text) ?: return null
        val serial = "${match.groupValues[1]}-${match.groupValues[2]}${match.groupValues[3]}"
        Logger.debug(TAG, "Found PS2 serial from CHD: $serial")
        return serial
    }

    private fun findPSXBootSerial(data: ByteArray): String? {
        val text = String(data, Charsets.ISO_8859_1)
        val pattern = Regex("""BOOT\s*=\s*cdrom:\\?([A-Z]{4})[_.](\d{3})\.(\d{2});""")
        val match = pattern.find(text) ?: return null
        val serial = "${match.groupValues[1]}-${match.groupValues[2]}${match.groupValues[3]}"
        Logger.debug(TAG, "Found PSX serial from CHD: $serial")
        return serial
    }

    private fun read48(buf: ByteBuffer): Long {
        val hi = buf.short.toLong() and 0xFFFF
        val lo = buf.int.toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    private class BitReader(private val data: ByteArray) {
        private var bitOffset = 0

        fun read(numBits: Int): Long {
            var result = 0L
            for (i in 0 until numBits) {
                val byteIndex = bitOffset / 8
                val bitIndex = 7 - (bitOffset % 8)
                if (byteIndex < data.size) {
                    val bit = (data[byteIndex].toInt() ushr bitIndex) and 1
                    result = (result shl 1) or bit.toLong()
                }
                bitOffset++
            }
            return result
        }

        fun peek(numBits: Int): Long {
            val saved = bitOffset
            val result = read(numBits)
            bitOffset = saved
            return result
        }

        fun advance(numBits: Int) {
            bitOffset += numBits
        }
    }

    private class HuffmanDecoder(private val numCodes: Int, private val maxBits: Int) {
        private val lookup = IntArray(1 shl maxBits)
        private val codeLengths = IntArray(numCodes)

        fun importTreeRle(reader: BitReader) {
            val numBitsPerEntry = when {
                maxBits >= 16 -> 5
                maxBits >= 8 -> 4
                else -> 3
            }

            var i = 0
            while (i < numCodes) {
                val value = reader.read(numBitsPerEntry).toInt()
                if (value != 1) {
                    codeLengths[i++] = value
                } else {
                    val next = reader.read(numBitsPerEntry).toInt()
                    if (next == 1) {
                        codeLengths[i++] = 1
                    } else {
                        val count = reader.read(numBitsPerEntry).toInt() + 3
                        repeat(count) { if (i < numCodes) codeLengths[i++] = 0 }
                    }
                }
            }

            buildLookupTable()
        }

        private fun buildLookupTable() {
            val histogram = IntArray(maxBits + 1)
            for (len in codeLengths) {
                if (len in 1..maxBits) histogram[len]++
            }

            val nextCode = IntArray(maxBits + 1)
            var code = 0
            for (bits in 1..maxBits) {
                code = (code + histogram[bits - 1]) shl 1
                nextCode[bits] = code
            }

            for (symbol in 0 until numCodes) {
                val len = codeLengths[symbol]
                if (len == 0 || len > maxBits) continue

                val symbolCode = nextCode[len]++
                val shift = maxBits - len
                val baseIndex = symbolCode shl shift
                val count = 1 shl shift

                for (j in 0 until count) {
                    val idx = baseIndex + j
                    if (idx < lookup.size) {
                        lookup[idx] = (symbol shl 5) or len
                    }
                }
            }
        }

        fun decode(reader: BitReader): Int {
            val bits = reader.peek(maxBits).toInt()
            val entry = lookup[bits]
            val symbol = entry ushr 5
            val length = entry and 0x1F
            if (length == 0) {
                reader.advance(1)
                return 0
            }
            reader.advance(length)
            return symbol
        }
    }
}
