package com.nendo.argosy.ui.screens.doodle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun DoodleScreen(
    onBack: () -> Unit,
    onPosted: () -> Unit,
    viewModel: DoodleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val captionFocusRequester = remember { FocusRequester() }
    var openKeyboard by remember { mutableStateOf(false) }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel, onBack) {
        DoodleInputHandler(
            viewModel = viewModel,
            onOpenKeyboard = { openKeyboard = true },
            onNavigateBack = onBack
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = "doodle")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = "doodle")
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(openKeyboard) {
        if (openKeyboard) {
            captionFocusRequester.requestFocus()
            openKeyboard = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DoodleEvent.Posted -> onPosted()
                is DoodleEvent.Error -> {}
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            LandscapeLayout(
                uiState = uiState,
                viewModel = viewModel,
                captionFocusRequester = captionFocusRequester
            )
        } else {
            PortraitLayout(
                uiState = uiState,
                viewModel = viewModel,
                captionFocusRequester = captionFocusRequester
            )
        }
    }

    if (uiState.showPostMenu) {
        PostMenuDialog(
            isPosting = uiState.isPosting,
            focusIndex = uiState.postMenuFocusIndex,
            onPost = { viewModel.post() },
            onCancel = { viewModel.hidePostMenu() }
        )
    }

    if (uiState.showDiscardDialog) {
        DiscardDialog(
            focusIndex = uiState.discardDialogFocusIndex,
            onDiscard = {
                viewModel.hideDiscardDialog()
                onBack()
            },
            onCancel = { viewModel.hideDiscardDialog() }
        )
    }

    if (uiState.showGamePicker) {
        GamePickerDialog(
            query = uiState.gamePickerQuery,
            results = uiState.gamePickerResults,
            focusIndex = uiState.gamePickerFocusIndex,
            searchFocused = uiState.gamePickerSearchFocused,
            onQueryChange = { viewModel.updateGamePickerQuery(it) },
            onSelectItem = { index ->
                viewModel.moveGamePickerFocus(index - uiState.gamePickerFocusIndex)
                viewModel.selectGame()
            },
            onDismiss = { viewModel.hideGamePicker() }
        )
    }
}

@Composable
private fun LandscapeLayout(
    uiState: DoodleUiState,
    viewModel: DoodleViewModel,
    captionFocusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                DoodleCanvas(
                    canvasSize = uiState.canvasSize,
                    pixels = uiState.pixels,
                    cursorX = uiState.cursorX,
                    cursorY = uiState.cursorY,
                    showCursor = uiState.currentSection == DoodleSection.CANVAS,
                    linePreview = uiState.linePreview,
                    selectedColor = uiState.selectedColor,
                    zoomLevel = uiState.zoomLevel,
                    panOffsetX = uiState.panOffsetX,
                    panOffsetY = uiState.panOffsetY,
                    onTap = { x, y -> viewModel.tapAt(x, y) },
                    onDrag = { x, y -> viewModel.drawAt(x, y) },
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (uiState.currentSection == DoodleSection.CANVAS) 2.dp else 1.dp,
                            color = if (uiState.currentSection == DoodleSection.CANVAS)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                )
            }

            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolSelector(
                    selectedTool = uiState.selectedTool,
                    onToolSelect = { viewModel.cycleTool() }
                )

                PaletteGrid(
                    selectedColor = uiState.selectedColor,
                    focusIndex = uiState.paletteFocusIndex,
                    isFocused = uiState.currentSection == DoodleSection.PALETTE,
                    onColorSelect = { viewModel.selectColor(it) },
                    columns = 8
                )

                SizeSelector(
                    selectedSize = uiState.canvasSize,
                    focusIndex = uiState.sizeFocusIndex,
                    isFocused = uiState.currentSection == DoodleSection.SIZE,
                    onSizeSelect = { viewModel.setCanvasSize(it) }
                )

                CaptionInput(
                    caption = uiState.caption,
                    onCaptionChange = { viewModel.setCaption(it) },
                    isFocused = uiState.currentSection == DoodleSection.CAPTION,
                    focusRequester = captionFocusRequester
                )

                GameSection(
                    linkedGameTitle = uiState.linkedGameTitle,
                    linkedGameCoverPath = uiState.linkedGameCoverPath,
                    isFocused = uiState.currentSection == DoodleSection.GAME,
                    onClick = { viewModel.showGamePicker() }
                )

                if (uiState.zoomLevel != ZoomLevel.FIT) {
                    ZoomIndicator(zoomLevel = uiState.zoomLevel)
                }
            }
        }

        DoodleFooter(
            currentSection = uiState.currentSection,
            selectedTool = uiState.selectedTool,
            isDrawing = uiState.isDrawing,
            hasContent = uiState.hasContent,
            linkedGameTitle = uiState.linkedGameTitle
        )
    }
}

@Composable
private fun PortraitLayout(
    uiState: DoodleUiState,
    viewModel: DoodleViewModel,
    captionFocusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolSelector(
                selectedTool = uiState.selectedTool,
                onToolSelect = { viewModel.cycleTool() }
            )

            SizeSelector(
                selectedSize = uiState.canvasSize,
                focusIndex = uiState.sizeFocusIndex,
                isFocused = uiState.currentSection == DoodleSection.SIZE,
                onSizeSelect = { viewModel.setCanvasSize(it) }
            )

            if (uiState.zoomLevel != ZoomLevel.FIT) {
                ZoomIndicator(zoomLevel = uiState.zoomLevel)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            DoodleCanvas(
                canvasSize = uiState.canvasSize,
                pixels = uiState.pixels,
                cursorX = uiState.cursorX,
                cursorY = uiState.cursorY,
                showCursor = uiState.currentSection == DoodleSection.CANVAS,
                linePreview = uiState.linePreview,
                selectedColor = uiState.selectedColor,
                zoomLevel = uiState.zoomLevel,
                panOffsetX = uiState.panOffsetX,
                panOffsetY = uiState.panOffsetY,
                onTap = { x, y -> viewModel.tapAt(x, y) },
                onDrag = { x, y -> viewModel.drawAt(x, y) },
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (uiState.currentSection == DoodleSection.CANVAS) 2.dp else 1.dp,
                        color = if (uiState.currentSection == DoodleSection.CANVAS)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PaletteGrid(
            selectedColor = uiState.selectedColor,
            focusIndex = uiState.paletteFocusIndex,
            isFocused = uiState.currentSection == DoodleSection.PALETTE,
            onColorSelect = { viewModel.selectColor(it) },
            columns = 16
        )

        Spacer(modifier = Modifier.height(12.dp))

        CaptionInput(
            caption = uiState.caption,
            onCaptionChange = { viewModel.setCaption(it) },
            isFocused = uiState.currentSection == DoodleSection.CAPTION,
            focusRequester = captionFocusRequester
        )

        Spacer(modifier = Modifier.height(8.dp))

        GameSection(
            linkedGameTitle = uiState.linkedGameTitle,
            linkedGameCoverPath = uiState.linkedGameCoverPath,
            isFocused = uiState.currentSection == DoodleSection.GAME,
            onClick = { viewModel.showGamePicker() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        DoodleFooter(
            currentSection = uiState.currentSection,
            selectedTool = uiState.selectedTool,
            isDrawing = uiState.isDrawing,
            hasContent = uiState.hasContent,
            linkedGameTitle = uiState.linkedGameTitle
        )
    }
}

@Composable
private fun ToolSelector(
    selectedTool: DoodleTool,
    onToolSelect: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DoodleTool.entries.forEach { tool ->
            val isSelected = tool == selectedTool
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onToolSelect() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (tool) {
                        DoodleTool.PEN -> Icons.Default.Edit
                        DoodleTool.LINE -> Icons.Default.Timeline
                        DoodleTool.FILL -> Icons.Default.FormatColorFill
                    },
                    contentDescription = when (tool) {
                        DoodleTool.PEN -> "Pen"
                        DoodleTool.LINE -> "Line"
                        DoodleTool.FILL -> "Fill"
                    },
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PaletteGrid(
    selectedColor: DoodleColor,
    focusIndex: Int,
    isFocused: Boolean,
    onColorSelect: (DoodleColor) -> Unit,
    columns: Int
) {
    val rows = 16 / columns

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                else Modifier
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(rows) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(columns) { col ->
                    val colorIndex = row * columns + col
                    if (colorIndex < 16) {
                        val color = DoodleColor.fromIndex(colorIndex)
                        val isColorSelected = color == selectedColor
                        val isColorFocused = isFocused && colorIndex == focusIndex

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(color.color)
                                .then(
                                    when {
                                        isColorFocused -> Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                        isColorSelected -> Modifier.border(
                                            2.dp,
                                            Color.White,
                                            CircleShape
                                        )
                                        else -> Modifier
                                    }
                                )
                                .clickable { onColorSelect(color) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeSelector(
    selectedSize: CanvasSize,
    focusIndex: Int,
    isFocused: Boolean,
    onSizeSelect: (CanvasSize) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                else Modifier
            )
            .padding(4.dp)
    ) {
        CanvasSize.entries.forEach { size ->
            val isSelected = size == selectedSize
            val isSizeFocused = isFocused && size.sizeEnum == focusIndex

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isSizeFocused -> MaterialTheme.colorScheme.primaryContainer
                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onSizeSelect(size) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${size.pixels}",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isSizeFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun CaptionInput(
    caption: String,
    onCaptionChange: (String) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                else Modifier
            )
            .padding(12.dp)
    ) {
        if (caption.isEmpty()) {
            Text(
                text = "Add a caption...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        BasicTextField(
            value = caption,
            onValueChange = onCaptionChange,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

@Composable
private fun ZoomIndicator(zoomLevel: ZoomLevel) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${zoomLevel.scale.toInt()}x",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun DoodleFooter(
    currentSection: DoodleSection,
    selectedTool: DoodleTool,
    isDrawing: Boolean,
    hasContent: Boolean,
    linkedGameTitle: String? = null
) {
    val hints = buildList {
        when (currentSection) {
            DoodleSection.CANVAS -> {
                add(InputButton.DPAD to "Move")
                val drawLabel = when {
                    selectedTool == DoodleTool.LINE && isDrawing -> "End"
                    isDrawing -> "Stop"
                    selectedTool == DoodleTool.LINE -> "Start"
                    selectedTool == DoodleTool.FILL -> "Fill"
                    else -> "Draw"
                }
                add(InputButton.A to drawLabel)
                add(InputButton.Y to "Tool")
            }
            DoodleSection.PALETTE -> {
                add(InputButton.DPAD to "Select")
                add(InputButton.A to "Pick")
            }
            DoodleSection.SIZE -> {
                add(InputButton.DPAD_HORIZONTAL to "Size")
                add(InputButton.A to "Confirm")
            }
            DoodleSection.CAPTION -> {
                add(InputButton.A to "Edit")
            }
            DoodleSection.GAME -> {
                add(InputButton.A to "Select")
                if (linkedGameTitle != null) {
                    add(InputButton.Y to "Clear")
                }
            }
        }
        add(InputButton.START to "Post")
        val backLabel = when {
            isDrawing -> "Cancel"
            hasContent -> "Discard"
            else -> "Back"
        }
        add(InputButton.B to backLabel)
    }

    FooterBar(hints = hints)
}

@Composable
private fun PostMenuDialog(
    isPosting: Boolean,
    focusIndex: Int,
    onPost: () -> Unit,
    onCancel: () -> Unit
) {
    Modal(title = "Post Doodle") {
        if (isPosting) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = "Posting...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Text(
                text = "Share your doodle with friends?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OptionItem(
                icon = Icons.Default.Send,
                label = "Post",
                isFocused = focusIndex == 0,
                onClick = onPost
            )
            OptionItem(
                icon = Icons.Default.Close,
                label = "Cancel",
                isFocused = focusIndex == 1,
                onClick = onCancel
            )
        }
    }
}

@Composable
private fun DiscardDialog(
    focusIndex: Int,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    Modal(title = "Discard Doodle?") {
        Text(
            text = "You have unsaved changes. Are you sure you want to discard your doodle?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OptionItem(
            icon = Icons.Default.Delete,
            label = "Discard",
            isFocused = focusIndex == 0,
            isDangerous = true,
            onClick = onDiscard
        )
        OptionItem(
            icon = Icons.Default.Edit,
            label = "Keep Editing",
            isFocused = focusIndex == 1,
            onClick = onCancel
        )
    }
}

@Composable
private fun GameSection(
    linkedGameTitle: String?,
    linkedGameCoverPath: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (linkedGameCoverPath != null) {
            val imageData = if (linkedGameCoverPath.startsWith("/")) {
                java.io.File(linkedGameCoverPath)
            } else {
                linkedGameCoverPath
            }
            AsyncImage(
                model = imageData,
                contentDescription = linkedGameTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        Text(
            text = linkedGameTitle ?: "No game selected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (linkedGameTitle != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun GamePickerDialog(
    query: String,
    results: List<GamePickerItem>,
    focusIndex: Int,
    searchFocused: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectItem: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(searchFocused) {
        if (searchFocused) {
            searchFocusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    Modal(
        title = "Select Game",
        baseWidth = 400.dp,
        onDismiss = onDismiss,
        footerHints = buildList {
            if (searchFocused) {
                add(InputButton.DPAD_DOWN to "Browse")
            } else {
                add(InputButton.DPAD to "Navigate")
                add(InputButton.A to "Select")
            }
            add(InputButton.B to "Cancel")
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (searchFocused) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .then(
                    if (searchFocused) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search games...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            item {
                OptionItem(
                    label = "None",
                    isFocused = !searchFocused && focusIndex == 0,
                    onClick = { onSelectItem(0) }
                )
            }
            itemsIndexed(results) { index, item ->
                OptionItem(
                    label = item.title,
                    value = item.platform,
                    isFocused = !searchFocused && focusIndex == index + 1,
                    onClick = { onSelectItem(index + 1) }
                )
            }
        }
    }
}
