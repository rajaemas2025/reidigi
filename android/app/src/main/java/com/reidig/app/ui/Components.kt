package com.reidig.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ButtonHeight = 48.dp
private val ButtonShape = RoundedCornerShape(50)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Primary,
            disabledContainerColor = if (loading) Palette.Primary else Palette.Disabled,
            disabledContentColor = Color.White,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Palette.Primary else Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Primary),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.CriticalSoft,
            contentColor = Palette.Critical,
            disabledContainerColor = Palette.CriticalSoft.copy(alpha = 0.5f),
            disabledContentColor = Palette.Critical.copy(alpha = 0.4f),
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun LightButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Palette.Primary,
            disabledContainerColor = Color.White.copy(alpha = 0.22f),
            disabledContentColor = Color.White.copy(alpha = 0.75f),
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun GhostOnDarkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
    ) {
        leading?.invoke()
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.Link) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
    )
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    prefix: String? = null,
    singleLine: Boolean = true,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (error != null) Palette.Critical else Palette.Ink)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            isError = error != null,
            placeholder = { Text(placeholder, color = Palette.DisabledText) },
            leadingIcon = prefix?.let {
                {
                    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.width(1.dp).height(22.dp).background(Palette.Line))
                    }
                }
            },
            trailingIcon = trailing,
            visualTransformation = if (password) PasswordVisualTransformation() else transformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            keyboardActions = KeyboardActions.Default,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Primary,
                unfocusedBorderColor = Palette.FieldBorder,
                errorBorderColor = Palette.CriticalBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                errorContainerColor = Color.White,
            ),
        )
        when {
            error != null -> Text(error, color = Palette.Critical, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            hint != null -> Text(hint, color = Palette.InkSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun ErrorLine(message: String, modifier: Modifier = Modifier, color: Color = Palette.Critical) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.ErrorOutline, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(message, color = color, fontSize = 13.sp)
    }
}

@Composable
fun RuleLine(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).background(if (met) Palette.Good else Palette.Disabled),
            contentAlignment = Alignment.Center,
        ) {
            if (met) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = if (met) Palette.Good else Palette.InkSoft)
    }
}

/** Kerangka layar putih: judul, langkah opsional, isi yang bisa digulir, tombol tetap di bawah. */
@Composable
fun FormScreen(
    title: String,
    onBack: (() -> Unit)?,
    step: Pair<Int, Int>? = null,
    bottomDivider: Boolean = false,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().imePadding()) {
        TopTitleBar(title, onBack)
        if (step != null) StepBar(step.first, step.second)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            content = content,
        )
        if (bottom != null) {
            if (bottomDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Ring))
            Column(
                Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(20.dp),
                content = bottom,
            )
        }
    }
}

@Composable
fun TopTitleBar(title: String, onBack: (() -> Unit)?, trailing: String? = null) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Palette.Primary)
            }
        }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.align(Alignment.Center))
        if (trailing != null) {
            Text(trailing, color = Palette.InkSoft, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        }
    }
}

/** Penanda langkah dari design system: lingkaran bernomor. */
@Composable
private fun StepBar(current: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clearAndSetSemantics { contentDescription = "Langkah $current dari $total" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (step in 1..total) {
            val reached = step <= current
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(if (reached) Palette.Primary else Color.White)
                    .border(1.dp, if (reached) Palette.Primary else Palette.FieldBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$step", color = if (reached) Color.White else Palette.FieldBorder, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (step < total) {
                Box(Modifier.weight(1f).height(1.5.dp).background(if (step < current) Palette.Primary else Palette.Line))
            }
        }
    }
}

/** Pilihan dua halaman di dalam satu tab. */
@Composable
fun SegmentSwitch(options: List<String>, selected: Int, inset: Dp = 20.dp, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 4.dp).clip(RoundedCornerShape(50)).background(Palette.Lavender).padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (active) Palette.Primary else Color.Transparent)
                    .clickable { if (!active) onSelect(index) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) Color.White else Palette.Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun Headline(title: String, subtitle: String? = null, centered: Boolean = false, subtitleRich: AnnotatedString? = null) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, lineHeight = 31.sp, color = Palette.Ink, textAlign = align, modifier = Modifier.fillMaxWidth())
    if (subtitle != null) {
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Palette.InkSoft, fontSize = 15.sp, lineHeight = 22.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
    if (subtitleRich != null) {
        Spacer(Modifier.height(6.dp))
        Text(subtitleRich, color = Palette.InkSoft, fontSize = 15.sp, lineHeight = 22.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun CodeDots(length: Int, total: Int = 6, error: Boolean = false, dotColor: Color = Palette.Primary) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "$length dari $total angka dimasukkan" },
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        repeat(total) { index ->
            val color = when {
                error -> Palette.Critical
                index < length -> dotColor
                else -> Color.Transparent
            }
            val border = when {
                error -> Palette.Critical
                index < length -> dotColor
                index == length -> dotColor
                else -> Palette.Line
            }
            Box(Modifier.size(16.dp).clip(CircleShape).background(color).border(1.5.dp, border, CircleShape))
        }
    }
}

@Composable
fun NumberPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, enabled: Boolean = true) {
    val rows = listOf("123", "456", "789")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { digit -> PadKey(digit.toString(), enabled, Modifier.weight(1f)) { onDigit(digit) } }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            PadKey("0", enabled, Modifier.weight(1f)) { onDigit('0') }
            Box(
                Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Hapus", tint = Palette.Ink)
            }
        }
    }
}

@Composable
private fun PadKey(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(60.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = if (enabled) Palette.Ink else Palette.DisabledText)
    }
}

private val CardEdge = 3.dp

/**
 * Membuat kartu terlihat timbul: bayangan, sisi bawah yang lebih gelap, dan kilau di tepi atas.
 * Isi dan efek sentuh dipasang setelah modifier ini agar tidak menutupi sisi bawah.
 */
fun Modifier.cardDepth(
    face: Color = Color.White,
    shape: Shape = RoundedCornerShape(16.dp),
    outline: Color? = null,
): Modifier = this
    .shadow(8.dp, shape, ambientColor = Palette.Primary.copy(alpha = 0.3f), spotColor = Palette.Primary.copy(alpha = 0.5f))
    .background(lerp(face, Palette.Primary, 0.22f), shape)
    .padding(bottom = CardEdge)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(lerp(face, Color.White, 0.7f), lerp(face, Palette.Primary, 0.025f))))
    .border(
        1.dp,
        if (outline != null) Brush.linearGradient(listOf(outline, outline))
        else Brush.verticalGradient(listOf(Color.White, lerp(face, Palette.Primary, 0.08f))),
        shape,
    )

/** [cardDepth] untuk kartu berwarna gelap dengan teks putih. */
fun Modifier.darkCardDepth(
    face: Color = Palette.NightSoft,
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier = this
    .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
    .background(lerp(face, Color.Black, 0.55f), shape)
    .padding(bottom = CardEdge)
    .clip(shape)
    .background(Brush.verticalGradient(listOf(lerp(face, Color.White, 0.14f), face)))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)), shape)

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, tint: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth().cardDepth(tint)
    Column(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(16.dp),
        content = content,
    )
}

enum class ChipTone { GOOD, WARN, CRITICAL, NEUTRAL, GOLD }

/** Status selalu dengan ikon dan label, bukan warna saja. */
@Composable
fun StatusChip(text: String, tone: ChipTone, icon: ImageVector = Icons.Outlined.CheckCircle) {
    val (bg, fg) = when (tone) {
        ChipTone.GOOD -> Palette.GoodSoft to Palette.Good
        ChipTone.WARN -> Palette.WarnSoft to Palette.Warn
        ChipTone.CRITICAL -> Palette.CriticalSoft to Palette.Critical
        ChipTone.NEUTRAL -> Palette.Ring to Palette.Primary
        ChipTone.GOLD -> Palette.GoldSoft to Palette.GoldDeep
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SummaryRow(label: String, value: String, strong: Boolean = false, valueColor: Color = Palette.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Palette.InkSoft, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium, fontSize = if (strong) 16.sp else 14.sp, color = valueColor, textAlign = TextAlign.End)
    }
}

@Composable
fun TokenCodeText(code: String, modifier: Modifier = Modifier) {
    Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, color = Palette.InkSoft, modifier = modifier)
}

val ScreenPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)

/** Menampilkan angka tanggal sebagai HH/BB/TTTT tanpa mengubah isi kolom, sehingga kursor tetap benar. */
object DateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatDateDigits(text.text)
        // Garis miring hanya ada kalau sudah ada angka sesudahnya, jadi batas posisi mengikuti panjang teks.
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val shifted = when {
                    offset <= 2 -> offset
                    offset <= 4 -> offset + 1
                    else -> offset + 2
                }
                return shifted.coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val shifted = when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    else -> offset - 2
                }
                return shifted.coerceAtMost(text.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

fun formatDateDigits(digits: String) = buildString {
    digits.forEachIndexed { index, c ->
        if (index == 2 || index == 4) append('/')
        append(c)
    }
}
