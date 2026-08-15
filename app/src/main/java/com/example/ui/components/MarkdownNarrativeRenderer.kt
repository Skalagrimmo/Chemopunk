package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Choice
import com.example.data.StoryNode
import com.example.data.narrative.InlineElement
import com.example.data.narrative.MarkdownBlock
import com.example.data.narrative.MarkdownNarrativeParser
import com.example.data.narrative.NarrativeScriptDocument
import com.example.data.narrative.StoryAssetDescriptor
import com.example.ui.theme.AcidYellow
import com.example.ui.theme.AmberTerminal
import com.example.ui.theme.ImmersiveAccentOrange
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveTeal
import com.example.ui.theme.ImmersiveText
import com.example.ui.theme.ImmersiveTextMuted
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.ToxicRed

/**
 * High-aesthetic Fallout 1 & 2 / Retro CRT Markdown Narrative Screen.
 * Renders parsed narrative AST elements with interactive choice nodes and script asset switching.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryDialogueScreen(
    document: NarrativeScriptDocument,
    currentNode: StoryNode?,
    availableAssets: List<StoryAssetDescriptor>,
    currentAssetFileName: String,
    onSelectAsset: (String) -> Unit,
    onSelectNode: (String) -> Unit,
    onChoiceSelected: (String) -> Unit,
    onOpenEditor: () -> Unit,
    onClose: () -> Unit,
    playerInventoryItemIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val activeNode = currentNode ?: document.storyNodes.values.firstOrNull()
    val scrollState = rememberLazyListState()

    // Scroll to top when active node changes
    LaunchedEffect(activeNode?.id) {
        scrollState.scrollToItem(0)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ImmersiveBackground.copy(alpha = 0.96f))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, ImmersiveTeal, RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = ImmersiveSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(PhosphorGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MARKDOWN NARRATIVE TERMINAL // ${document.assetFileName}",
                            color = ImmersiveTeal,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenEditor,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_edit_markdown_script")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Script", tint = ImmersiveTeal, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_close_story_modal")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ImmersiveTeal, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Asset Selector Tabs (Campaign, Audio Logs, Terminal Archives, Survival Manual)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availableAssets) { asset ->
                        val isSelected = asset.fileName == currentAssetFileName
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectAsset(asset.fileName) }
                                .border(
                                    1.dp,
                                    if (isSelected) ImmersiveTeal else ImmersiveSurfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .testTag("tab_asset_${asset.fileName.substringBefore(".md")}"),
                            color = if (isSelected) ImmersiveTeal.copy(alpha = 0.2f) else ImmersiveBackground
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "[${asset.category}]",
                                    color = if (isSelected) ImmersiveAccentOrange else ImmersiveTextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = asset.title,
                                    color = if (isSelected) ImmersiveText else ImmersiveTextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Story Node Quick-Navigation Drawer / Chips if multiple nodes in document
                if (document.storyNodes.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ImmersiveBackground, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(document.storyNodes.values.toList()) { node ->
                            val isCurrent = node.id == activeNode?.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSelectNode(node.id) }
                                    .background(
                                        if (isCurrent) ImmersiveAccentOrange.copy(alpha = 0.25f)
                                        else ImmersiveSurface
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrent) ImmersiveAccentOrange else ImmersiveSurfaceVariant,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("chip_node_${node.id}")
                            ) {
                                Text(
                                    text = "#${node.id}",
                                    color = if (isCurrent) ImmersiveAccentOrange else ImmersiveTextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Main Markdown Narrative Content Area
                if (activeNode != null) {
                    val parsedBlocks = remember(activeNode.content) {
                        MarkdownNarrativeParser.parseBlocks(activeNode.content)
                    }

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(ImmersiveBackground, RoundedCornerShape(12.dp))
                            .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                            .testTag("narrative_content_scroll")
                    ) {
                        // Node Header & Metadata Card
                        item {
                            StoryNodeHeader(node = activeNode)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Render Parsed Markdown Blocks (Headings, Paragraphs, Quotes, CodeBlocks, Lists, Tables)
                        items(parsedBlocks) { block ->
                            RenderMarkdownBlock(
                                block = block,
                                onChoiceSelected = onChoiceSelected
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Interactive Story Choices / Decision Branches
                        if (activeNode.choices.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "► TACTICAL DECISION PROTOCOLS:",
                                    color = ImmersiveAccentOrange,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            items(activeNode.choices) { choice ->
                                ChoiceCard(
                                    choice = choice,
                                    playerInventoryItemIds = playerInventoryItemIds,
                                    onChoiceSelected = onChoiceSelected
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                } else {
                    // Document-level standalone fallback view
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp)
                    ) {
                        items(document.standaloneBlocks) { block ->
                            RenderMarkdownBlock(block = block, onChoiceSelected = onChoiceSelected)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Story Node header displaying the title, speaker avatar badge, mood tag, and category.
 */
@Composable
fun StoryNodeHeader(node: StoryNode) {
    val moodColor = when (node.mood) {
        "HAZARD" -> ToxicRed
        "WARNING" -> AcidYellow
        "GLITCH" -> Color(0xFFFF007F)
        "CRYPTIC" -> Color(0xFF38BDF8)
        else -> ImmersiveTeal
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category & ID Tag
            Box(
                modifier = Modifier
                    .background(ImmersiveSurfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${node.category} // NODE_${node.id.uppercase()}",
                    color = ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Mood Badge
            Box(
                modifier = Modifier
                    .background(moodColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .border(0.8.dp, moodColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "MOOD: ${node.mood}",
                    color = moodColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = node.title,
            color = ImmersiveText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp
        )

        // Speaker Line if specified
        if (!node.speaker.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TRANSMISSION FROM: ",
                    color = ImmersiveTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Text(
                    text = node.speaker,
                    color = ImmersiveAccentOrange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Recursive renderer for all AST Markdown Block elements.
 */
@Composable
fun RenderMarkdownBlock(
    block: MarkdownBlock,
    onChoiceSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (block) {
        is MarkdownBlock.Header -> {
            val (size, color, weight) = when (block.level) {
                1 -> Triple(18.sp, ImmersiveTeal, FontWeight.Bold)
                2 -> Triple(15.sp, ImmersiveAccentOrange, FontWeight.Bold)
                3 -> Triple(13.sp, AcidYellow, FontWeight.Bold)
                else -> Triple(12.sp, PhosphorGreen, FontWeight.SemiBold)
            }
            Column(modifier = modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedStringFromInlines(block.inlines),
                    fontSize = size,
                    color = color,
                    fontWeight = weight,
                    fontFamily = FontFamily.Monospace
                )
                if (block.level <= 2) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(color.copy(alpha = 0.4f))
                    )
                }
            }
        }

        is MarkdownBlock.Paragraph -> {
            Text(
                text = buildAnnotatedStringFromInlines(block.inlines),
                color = ImmersiveText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = modifier.fillMaxWidth()
            )
        }

        is MarkdownBlock.Blockquote -> {
            val barColor = if (block.isHazard) ToxicRed else ImmersiveTeal
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(barColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(barColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    if (block.speaker != null) {
                        Text(
                            text = "[${block.speaker}]",
                            color = barColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = buildAnnotatedStringFromInlines(block.inlines),
                        color = if (block.isHazard) AcidYellow else ImmersiveText,
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        is MarkdownBlock.CodeBlock -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070E14), RoundedCornerShape(8.dp))
                    .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    if (block.language.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CODE: ${block.language.uppercase()}",
                                color = ImmersiveTeal,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = block.code,
                        color = PhosphorGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        is MarkdownBlock.BulletList -> {
            Column(modifier = modifier.fillMaxWidth()) {
                block.items.forEach { itemInlines ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "▪ ",
                            color = ImmersiveTeal,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildAnnotatedStringFromInlines(itemInlines),
                            color = ImmersiveText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        is MarkdownBlock.NumberedList -> {
            Column(modifier = modifier.fillMaxWidth()) {
                block.items.forEach { (num, itemInlines) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "$num. ",
                            color = ImmersiveAccentOrange,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildAnnotatedStringFromInlines(itemInlines),
                            color = ImmersiveText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        is MarkdownBlock.MarkdownTable -> {
            val horizontalScrollState = rememberScrollState()
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .background(ImmersiveSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    // Table Header Row
                    Row(
                        modifier = Modifier
                            .background(ImmersiveSurfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp)
                    ) {
                        block.headers.forEach { header ->
                            Text(
                                text = header,
                                color = ImmersiveTeal,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .width(110.dp)
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                    Divider(color = ImmersiveSurfaceVariant, thickness = 1.dp)
                    // Table Data Rows
                    block.rows.forEachIndexed { idx, row ->
                        Row(
                            modifier = Modifier
                                .background(
                                    if (idx % 2 == 0) Color.Transparent
                                    else ImmersiveBackground.copy(alpha = 0.5f)
                                )
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            row.forEach { cell ->
                                Text(
                                    text = cell,
                                    color = ImmersiveText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .width(110.dp)
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        is MarkdownBlock.ChoiceAction -> {
            ChoiceCard(
                choice = block.choice,
                playerInventoryItemIds = emptySet(),
                onChoiceSelected = onChoiceSelected
            )
        }

        MarkdownBlock.HorizontalRule -> {
            Divider(
                color = ImmersiveSurfaceVariant,
                thickness = 1.dp,
                modifier = modifier.padding(vertical = 6.dp)
            )
        }
    }
}

/**
 * Interactive Decision Option Card with requirement validation badges and toxicity modifiers.
 */
@Composable
fun ChoiceCard(
    choice: Choice,
    playerInventoryItemIds: Set<String>,
    onChoiceSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasRequirement = choice.requiredItemId == null || playerInventoryItemIds.contains(choice.requiredItemId)

    Button(
        onClick = {
            if (hasRequirement) {
                onChoiceSelected(choice.targetNodeId)
            }
        },
        enabled = hasRequirement,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (hasRequirement) ImmersiveTeal else ImmersiveSurfaceVariant,
                RoundedCornerShape(10.dp)
            )
            .testTag("btn_choice_${choice.targetNodeId}"),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (hasRequirement) ImmersiveTeal.copy(alpha = 0.15f) else ImmersiveSurfaceVariant.copy(alpha = 0.3f),
            contentColor = if (hasRequirement) ImmersiveTeal else ImmersiveTextMuted,
            disabledContainerColor = ImmersiveSurfaceVariant.copy(alpha = 0.2f),
            disabledContentColor = ImmersiveTextMuted
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "> ${choice.text}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Optional requirement / modifier tags
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (choice.toxicityCost != 0) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (choice.toxicityCost < 0) PhosphorGreen.copy(alpha = 0.2f)
                                    else ToxicRed.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (choice.toxicityCost < 0) "${choice.toxicityCost}% TOX" else "+${choice.toxicityCost}% TOX",
                                color = if (choice.toxicityCost < 0) PhosphorGreen else ToxicRed,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (choice.requiredItemId != null) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (hasRequirement) ImmersiveAccentOrange.copy(alpha = 0.2f)
                                    else ToxicRed.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (hasRequirement) "REQ: ${choice.requiredItemId}" else "LOCKED: ${choice.requiredItemId}",
                                color = if (hasRequirement) ImmersiveAccentOrange else ToxicRed,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds an AnnotatedString from a list of InlineElements with cybernetic color coding.
 */
fun buildAnnotatedStringFromInlines(inlines: List<InlineElement>): AnnotatedString {
    return buildAnnotatedString {
        for (inline in inlines) {
            when (inline) {
                is InlineElement.Text -> {
                    append(inline.content)
                }
                is InlineElement.Bold -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ImmersiveText))
                    append(inline.content)
                    pop()
                }
                is InlineElement.Italic -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = ImmersiveTextMuted))
                    append(inline.content)
                    pop()
                }
                is InlineElement.BoldItalic -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = AcidYellow))
                    append(inline.content)
                    pop()
                }
                is InlineElement.InlineCode -> {
                    pushStyle(
                        SpanStyle(
                            background = Color(0xFF0F232D),
                            color = ImmersiveTeal,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(" ${inline.code} ")
                    pop()
                }
                is InlineElement.Strikethrough -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = ImmersiveTextMuted))
                    append(inline.content)
                    pop()
                }
                is InlineElement.Highlight -> {
                    pushStyle(
                        SpanStyle(
                            background = AcidYellow.copy(alpha = 0.25f),
                            color = AcidYellow,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(inline.content)
                    pop()
                }
                is InlineElement.Link -> {
                    pushStyle(
                        SpanStyle(
                            color = if (inline.isStoryLink) ImmersiveAccentOrange else ImmersiveTeal,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    append(inline.label)
                    pop()
                }
            }
        }
    }
}
