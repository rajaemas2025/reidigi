package com.reidig.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
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
            contentColor = Palette.Primary50,
            disabledContainerColor = if (loading) Palette.Primary else Palette.Neutral200,
            disabledContentColor = if (loading) Palette.Primary50 else Palette.DisabledText,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.5.dp, if (enabled) Palette.Primary else Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Palette.Primary,
            disabledContentColor = Palette.DisabledText,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.5.dp, Palette.Critical),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Palette.Critical,
            disabledContentColor = Palette.Critical.copy(alpha = 0.4f),
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun GhostOnDarkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
    ) {
        leading?.invoke()
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Palette.Primary) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
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
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (error != null) Palette.Critical else Palette.Ink)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            isError = error != null,
            placeholder = { Text(placeholder, color = Palette.DisabledText, fontSize = 15.sp) },
            leadingIcon = when {
                prefix != null -> {
                    {
                        Row(Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                prefix,
                                color = Palette.InkSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.width(1.dp).height(22.dp).background(Palette.Line))
                        }
                    }
                }
                leadingIcon != null -> {
                    {
                        Icon(leadingIcon, null, tint = Palette.InkSoft, modifier = Modifier.size(18.dp))
                    }
                }
                else -> null
            },
            trailingIcon = trailing,
            visualTransformation = if (password) PasswordVisualTransformation() else transformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            keyboardActions = KeyboardActions.Default,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Primary,
                unfocusedBorderColor = Palette.FieldBorder,
                errorBorderColor = Palette.CriticalBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                errorContainerColor = Palette.CriticalSoft,
            ),
        )
        when {
            error != null -> {
                Spacer(Modifier.height(6.dp))
                ErrorLine(error)
            }
            hint != null -> Text(hint, color = Palette.InkSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun ErrorLine(message: String, modifier: Modifier = Modifier, color: Color = Palette.Critical) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.ErrorOutline, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(message, color = color, fontSize = 12.5.sp)
    }
}

@Composable
fun RuleLine(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(
            Modifier.size(16.dp).clip(CircleShape).background(if (met) Palette.Good else Palette.Neutral200),
            contentAlignment = Alignment.Center,
        ) {
            if (met) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (met) FontWeight.SemiBold else FontWeight.Normal,
            color = if (met) Palette.Good else Palette.InkSoft,
        )
    }
}

/** Kerangka layar: latar neutral-100 (#F5F5F5), top bar, indikator langkah opsional, isi gulir, dan aksi bawah. */
@Composable
fun FormScreen(
    title: String,
    onBack: (() -> Unit)?,
    step: Pair<Int, Int>? = null,
    stepLabels: List<String>? = null,
    bottomDivider: Boolean = false,
    trailingAction: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding().imePadding()) {
        TopTitleBar(
            title = title,
            onBack = onBack,
            trailing = if (step != null && stepLabels == null) "${step.first}/${step.second}" else null,
            trailingContent = trailingAction,
        )
        if (step != null) {
            if (stepLabels != null) {
                LabeledStepBar(step.first, stepLabels)
            } else {
                SegmentedStepBar(step.first, step.second)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
            content = content,
        )
        if (bottom != null) {
            if (bottomDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line.copy(alpha = 0.5f)))
            Column(
                Modifier.fillMaxWidth().background(Palette.Paper).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                content = bottom,
            )
        }
    }
}

@Composable
fun TopTitleBar(
    title: String,
    onBack: (() -> Unit)?,
    trailing: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Kembali", tint = Palette.Ink, modifier = Modifier.size(26.dp))
            }
        } else {
            Spacer(Modifier.size(44.dp))
        }
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        when {
            trailingContent != null -> trailingContent()
            trailing != null -> {
                Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        trailing,
                        color = Palette.InkSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            else -> Spacer(Modifier.size(44.dp))
        }
    }
}

/** Indikator langkah berlabel seperti pada alur registrasi prototipe (Buat akun - Verifikasi - Data diri). */
@Composable
fun LabeledStepBar(current: Int, labels: List<String>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = "Langkah $current dari ${labels.size}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val step = i + 1
            val done = step < current
            val active = step == current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (done || active) Palette.Primary else Palette.Neutral200),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    } else {
                        Text(
                            "$step",
                            color = if (active) Color.White else Palette.InkSoft,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (done || active) Palette.Primary else Palette.InkSoft,
                )
            }
            if (step < labels.size) {
                Box(
                    Modifier.weight(1f).padding(horizontal = 8.dp).height(2.dp)
                        .background(if (done) Palette.Primary else Palette.Line),
                )
            }
        }
    }
}

/** Bar progres terpisah (segmented) seperti pada alur e-KYC prototipe. */
@Composable
fun SegmentedStepBar(current: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = "Langkah $current dari $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (step in 1..total) {
            Box(
                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                    .background(if (step <= current) Palette.Primary else Palette.Neutral200),
            )
        }
    }
}

/** Pilihan dua halaman di dalam satu tab (pil putih di atas latar neutral-200). */
@Composable
fun SegmentSwitch(options: List<String>, selected: Int, inset: Dp = 20.dp, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.Neutral200)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.weight(1f)
                    .then(if (active) Modifier.shadow(2.dp, RoundedCornerShape(50)) else Modifier)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { if (!active) onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Palette.Ink else Palette.InkSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                )
            }
        }
    }
}

@Composable
fun Headline(
    title: String,
    subtitle: String? = null,
    centered: Boolean = false,
    titleColor: Color = Palette.Ink,
    subtitleRich: AnnotatedString? = null,
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Text(
        title,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = titleColor,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
    if (subtitle != null) {
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Palette.InkSecondary, fontSize = 14.sp, lineHeight = 21.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
    if (subtitleRich != null) {
        Spacer(Modifier.height(6.dp))
        Text(subtitleRich, color = Palette.InkSecondary, fontSize = 14.sp, lineHeight = 21.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
    }
}

/** Kotak 6 digit untuk OTP dan PIN sesuai desain prototipe (48x56dp, radius 12dp). */
@Composable
fun CodeBoxes(
    code: String,
    total: Int = 6,
    masked: Boolean = false,
    error: Boolean = false,
    verified: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "${code.length} dari $total angka dimasukkan" },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(total) { index ->
            val char = code.getOrNull(index)
            val isActive = index == code.length && !verified
            val bg = when {
                verified -> Palette.GoodSoft
                error -> Palette.CriticalSoft
                else -> Color.White
            }
            val borderColor = when {
                verified -> Palette.Good
                error -> Palette.Critical
                isActive || char != null -> Palette.Primary
                else -> Palette.Line
            }
            Box(
                Modifier.weight(1f, fill = false).width(46.dp).height(56.dp)
                    .then(
                        if (isActive) Modifier.border(3.dp, Palette.Primary200, RoundedCornerShape(14.dp))
                        else Modifier
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(if (isActive || char != null || error || verified) 2.dp else 1.5.dp, borderColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    char != null && masked -> {
                        Box(
                            Modifier.size(12.dp).clip(CircleShape)
                                .background(if (verified) Palette.Good else Palette.Primary),
                        )
                    }
                    char != null -> {
                        Text(
                            char.toString(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (verified) Palette.Good else Palette.Ink,
                        )
                    }
                    isActive -> {
                        Box(Modifier.width(2.dp).height(22.dp).background(Palette.Primary))
                    }
                }
            }
        }
    }
}

@Composable
fun CodeDots(length: Int, total: Int = 6, error: Boolean = false, dotColor: Color = Palette.Primary) {
    CodeBoxes(code = "•".repeat(length.coerceAtMost(total)), total = total, masked = true, error = error)
}

@Composable
fun NumberPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, enabled: Boolean = true) {
    val rows = listOf("123", "456", "789")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit -> PadKey(digit.toString(), enabled, Modifier.weight(1f)) { onDigit(digit) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            PadKey("0", enabled, Modifier.weight(1f)) { onDigit('0') }
            Box(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, "Hapus", tint = Palette.InkSecondary)
            }
        }
    }
}

@Composable
private fun PadKey(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (enabled) Palette.Ink else Palette.DisabledText,
        )
    }
}

/**
 * Gaya kartu permukaan bersih sesuai desain prototipe: latar putih/tint, garis tepi 1dp halus, dan sudut membulat 16dp.
 */
fun Modifier.cardDepth(
    face: Color = Color.White,
    shape: Shape = RoundedCornerShape(16.dp),
    outline: Color? = null,
): Modifier = this
    .shadow(1.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.08f))
    .clip(shape)
    .background(face, shape)
    .border(1.dp, outline ?: Palette.Line, shape)

/** Kartu ungu tua (#371F76) sesuai desain prototipe. */
fun Modifier.darkCardDepth(
    face: Color = Palette.Primary,
    shape: Shape = RoundedCornerShape(24.dp),
): Modifier = this
    .shadow(6.dp, shape, ambientColor = Palette.Primary.copy(alpha = 0.25f), spotColor = Palette.Primary.copy(alpha = 0.35f))
    .clip(shape)
    .background(face, shape)

/** Kartu hero ungu tua dengan lingkaran dekoratif di sudut kanan atas seperti pada prototipe. */
@Composable
fun HeroPurpleCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.fillMaxWidth().darkCardDepth(Palette.Primary, shape),
    ) {
        Box(
            Modifier.size(168.dp)
                .align(Alignment.TopEnd)
                .offset(x = 48.dp, y = (-48).dp)
                .clip(CircleShape)
                .background(Palette.Primary600.copy(alpha = 0.5f)),
        )
        Box(Modifier.fillMaxWidth().padding(22.dp), content = content)
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color = Color.White,
    outline: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = outline ?: when (tint) {
        Palette.Primary50 -> Palette.Primary100
        Palette.GoldSoft -> Palette.Gold200
        Palette.WarnSoft -> Palette.WarnBorder
        Palette.GoodSoft -> Palette.Good.copy(alpha = 0.3f)
        Palette.CriticalSoft -> Palette.Critical.copy(alpha = 0.3f)
        else -> Palette.Line
    }
    val base = modifier.fillMaxWidth().cardDepth(tint, RoundedCornerShape(16.dp), border)
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
        ChipTone.GOOD -> Palette.GoodSoft to Palette.Good700
        ChipTone.WARN -> Palette.WarnSoft to Palette.Warn
        ChipTone.CRITICAL -> Palette.CriticalSoft to Palette.Critical
        ChipTone.NEUTRAL -> Palette.Primary50 to Palette.Primary
        ChipTone.GOLD -> Palette.GoldSoft to Palette.GoldDeep
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
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
        Text(label, color = if (strong) Palette.Ink else Palette.InkSecondary, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, fontSize = if (strong) 14.sp else 13.5.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = if (strong) 16.sp else 13.5.sp,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun TokenCodeText(code: String, modifier: Modifier = Modifier) {
    Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.InkSoft, modifier = modifier)
}

/** Lencana lingkaran ganda (double-ring badge) untuk layar selesai registrasi, e-KYC, dan transaksi. */
@Composable
fun DoubleRingSuccessBadge(icon: ImageVector = Icons.Filled.Check) {
    Box(
        Modifier.size(120.dp).clip(CircleShape).background(Palette.Primary100),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(92.dp).clip(CircleShape).background(Palette.Primary)
                .border(4.dp, Palette.Primary200, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Palette.Gold, modifier = Modifier.size(44.dp))
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)

/** Menampilkan angka tanggal sebagai HH/BB/TTTT tanpa mengubah isi kolom, sehingga kursor tetap benar. */
object DateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatDateDigits(text.text)
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
