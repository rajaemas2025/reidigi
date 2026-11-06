package com.reidig.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reidig.shared.MockRepository
import com.reidig.shared.Rules
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val STEPS = 5

@Composable
fun KycConsentScreen(onBack: () -> Unit, onNext: () -> Unit) {
    var terms by rememberSaveable { mutableStateOf(false) }
    var data by rememberSaveable { mutableStateOf(false) }
    var offers by rememberSaveable { mutableStateOf(false) }
    FormScreen(
        "Verifikasi identitas",
        onBack,
        step = 1 to STEPS,
        bottomDivider = true,
        bottom = {
            PrimaryButton("Setuju & mulai verifikasi", onNext, enabled = terms && data)
            if (!(terms && data)) {
                Spacer(Modifier.height(8.dp))
                Text("Centang dua pernyataan wajib untuk lanjut.", color = Palette.InkSoft, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        HeroPurpleCard(shape = RoundedCornerShape(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Shield, null, tint = Palette.Gold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Verifikasi sekali untuk mulai membeli emas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, lineHeight = 20.sp)
                    Text("Cukup sekitar 2 menit", color = Palette.Primary200, fontSize = 12.5.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Headline("Ketentuan verifikasi (e-KYC)", "Wajib sebelum transaksi emas pertama.", titleColor = Palette.Primary)
        Spacer(Modifier.height(14.dp))
        SurfaceCard {
            PolicyItem("1. Akun Anda", "Usia minimal 18 tahun dengan e-KTP yang berlaku. Satu orang satu akun.")
            PolicyItem("2. Perdagangan emas", "Keping emas disimpan di brankas dan dicatat per nomor seri. Harga dikunci sebentar saat Anda bertransaksi.")
            PolicyItem("3. Verifikasi identitas", "Kami memeriksa e-KTP dan wajah Anda sesuai ketentuan regulator & APU-PPT.")
            PolicyItem("4. Privasi data", "Data dienkripsi AES-256 dan tidak diperjualbelikan.", isLast = true)
        }
        Spacer(Modifier.height(14.dp))
        ConsentRow("Saya sudah membaca dan menyetujui Ketentuan Layanan. *", terms) { terms = it }
        ConsentRow("Saya menyetujui pemrosesan data pribadi untuk verifikasi identitas. *", data) { data = it }
        ConsentRow("Kirimkan info harga dan penawaran (opsional).", offers) { offers = it }
    }
}

@ScreenPreview
@Composable
private fun KycConsentPreview() = ReidigTheme { KycConsentScreen({}, {}) }

@Composable
private fun PolicyItem(title: String, body: String, isLast: Boolean = false) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Palette.Ink)
    Spacer(Modifier.height(3.dp))
    Text(body, color = Palette.InkSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    if (!isLast) Spacer(Modifier.height(12.dp))
}

@Composable
internal fun ConsentRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .clip(shape)
            .background(if (checked) Palette.Primary50 else Color.White)
            .border(if (checked) 2.dp else 1.dp, if (checked) Palette.Primary else Palette.Line, shape)
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, null, colors = CheckboxDefaults.colors(checkedColor = Palette.Primary, uncheckedColor = Palette.Line))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.5.sp, lineHeight = 19.sp, color = Palette.Ink)
    }
}

@Composable
fun KycPhoneScreen(kyc: KycDraft, onBack: () -> Unit, onNext: () -> Unit) {
    var phone by rememberSaveable { mutableStateOf(kyc.phone) }
    val valid = Rules.isValidIndonesianPhone(phone)
    FormScreen(
        "Verifikasi nomor HP",
        onBack,
        step = 2 to STEPS,
        bottomDivider = true,
        bottom = { PrimaryButton("Lanjutkan", { kyc.phone = phone; onNext() }, enabled = valid) },
    ) {
        Headline(
            "Masukkan nomor HP aktif",
            "Kami akan mengirimkan kode OTP untuk memastikan nomor ini milikmu.",
            titleColor = Palette.Primary,
        )
        Spacer(Modifier.height(22.dp))
        FormField(
            "Nomor HP",
            phone,
            { phone = it.filter { c -> c.isDigit() }.trimStart('0') },
            prefix = "+62",
            placeholder = "81234567890",
            keyboardType = KeyboardType.Phone,
            hint = "Kode verifikasi dikirim ke nomor ini.",
            error = if (phone.length >= 4 && !valid && phone.length !in 9..12) "Masukkan nomor HP yang valid." else if (phone.isNotEmpty() && !phone.startsWith("8")) "Nomor HP diawali angka 8." else null,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = Palette.InkSoft, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text("Data Anda dienkripsi saat dikirim.", color = Palette.InkSoft, fontSize = 12.5.sp)
        }
    }
}

@ScreenPreview
@Composable
private fun KycPhonePreview() = ReidigTheme { KycPhoneScreen(previewKyc(), {}, {}) }

@Composable
fun KycChannelScreen(kyc: KycDraft, onBack: () -> Unit, onNext: () -> Unit) {
    var channel by rememberSaveable { mutableStateOf(kyc.channel) }
    FormScreen(
        "Verifikasi nomor HP",
        onBack,
        step = 2 to STEPS,
        bottomDivider = true,
        bottom = { PrimaryButton("Kirim kode lewat $channel", { kyc.channel = channel; onNext() }) },
    ) {
        Headline("Kirim OTP lewat", "Ke nomor +62 ${kyc.phone}", titleColor = Palette.Primary)
        Spacer(Modifier.height(22.dp))
        ChannelCard("WhatsApp", "Direkomendasikan · lebih cepat", Icons.AutoMirrored.Filled.Chat, Color(0xFF16A34A), channel == "WhatsApp") { channel = "WhatsApp" }
        Spacer(Modifier.height(12.dp))
        ChannelCard("SMS", "Pulsa reguler operator", Icons.Filled.Sms, Palette.Primary, channel == "SMS") { channel = "SMS" }
    }
}

@ScreenPreview
@Composable
private fun KycChannelPreview() = ReidigTheme { KycChannelScreen(previewKyc(), {}, {}) }

@Composable
private fun ChannelCard(title: String, subtitle: String, icon: ImageVector, tint: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) Palette.Primary50 else Color.White)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) Palette.Primary else Palette.Line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.Ink)
            Text(subtitle, color = Palette.InkSecondary, fontSize = 12.5.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (selected) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Palette.Primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(Modifier.size(22.dp).border(1.5.dp, Palette.Line, CircleShape))
        }
    }
}

private enum class ScanStage { IDLE, CAPTURING, FLASH, SCANNING, CAPTURED }

/**
 * Kamera sungguhan untuk foto KTP. Foto hanya disimpan di memori dan hilang saat layar ditutup.
 */
@Composable
fun KycScanIdScreen(onBack: () -> Unit, onCaptured: () -> Unit) {
    CameraPermissionGate("Foto e-KTP", "Izinkan kamera untuk memotret e-KTP", onBack) {
        KtpCamera(onBack, onCaptured)
    }
}

@ScreenPreview
@Composable
private fun KycScanIdPreview() = ReidigTheme { KycScanIdScreen({}, {}) }

@Composable
private fun CameraPermissionGate(title: String, request: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (LocalInspectionMode.current) {
        content()
        return
    }
    val context = LocalContext.current
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasPermission()) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        denied = !result
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) {
        content()
    } else {
        CameraScaffold(title, onBack, request) {
            Text(
                "Kamera hanya dipakai untuk verifikasi identitas.",
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            if (denied) {
                LightButton("Buka Pengaturan", {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                })
                Spacer(Modifier.height(10.dp))
            }
            LightButton("Izinkan kamera", { launcher.launch(Manifest.permission.CAMERA) })
        }
    }
}

@Composable
private fun KtpCamera(onBack: () -> Unit, onCaptured: () -> Unit) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf(ScanStage.IDLE) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val inPreview = LocalInspectionMode.current
    val imageCapture = remember {
        if (inPreview) null else ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val previewView = rememberBoundPreview(
        CameraSelector.DEFAULT_BACK_CAMERA,
        imageCapture,
        onReady = { error = null },
        onError = { error = "Kamera tidak bisa dibuka. Coba lagi." },
    )
    LaunchedEffect(stage) {
        when (stage) {
            ScanStage.FLASH -> { delay(120); stage = ScanStage.SCANNING }
            ScanStage.SCANNING -> { delay(1400); stage = ScanStage.CAPTURED }
            ScanStage.CAPTURED -> { delay(900); onCaptured() }
            else -> Unit
        }
    }

    val message = when (stage) {
        ScanStage.CAPTURING, ScanStage.FLASH, ScanStage.SCANNING -> "Tahan… sedang membaca data e-KTP"
        ScanStage.CAPTURED -> "e-KTP berhasil difoto"
        ScanStage.IDLE -> error ?: "Posisikan e-KTP tepat di dalam bingkai"
    }
    CameraScaffold("Foto e-KTP", onBack, message) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.58f), contentAlignment = Alignment.Center) {
            CameraSurface(previewView, Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(14.dp)))
            photo?.let {
                Image(it.asImageBitmap(), "Foto KTP", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(14.dp)))
            }
            IdFrame(stage)
            if (stage == ScanStage.FLASH) Box(Modifier.fillMaxSize().background(Color.White))
            if (stage == ScanStage.CAPTURED) SuccessBadge()
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("e-KTP asli", "Cahaya cukup", "Semua sudut jelas").forEach { CameraTip(it) }
        }
        Spacer(Modifier.height(20.dp))
        ShutterButton(enabled = stage == ScanStage.IDLE) {
            stage = ScanStage.CAPTURING
            imageCapture?.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val rotation = image.imageInfo.rotationDegrees
                        val raw = image.toBitmap()
                        image.close()
                        photo = if (rotation == 0) raw else {
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                        }
                        stage = ScanStage.FLASH
                    }

                    override fun onError(exception: ImageCaptureException) {
                        error = "Gagal memotret. Coba lagi."
                        stage = ScanStage.IDLE
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Ketuk tombol untuk ambil foto", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CameraSurface(previewView: PreviewView?, modifier: Modifier) {
    if (previewView != null) {
        AndroidView({ previewView }, modifier)
    } else {
        Box(modifier.background(Color(0xFF1F1638)))
    }
}

@Composable
private fun rememberBoundPreview(
    selector: CameraSelector,
    imageCapture: ImageCapture? = null,
    onReady: () -> Unit,
    onError: () -> Unit,
): PreviewView? {
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                val useCases = listOfNotNull(preview, imageCapture).toTypedArray()
                provider.bindToLifecycle(lifecycleOwner, selector, *useCases)
                onReady()
            } catch (e: Exception) {
                onError()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) runCatching { future.get().unbindAll() }
        }
    }
    return previewView
}

@Composable
private fun IdFrame(stage: ScanStage) {
    val transition = rememberInfiniteTransition(label = "scan")
    val line by transition.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "line")
    val cornerColor = if (stage == ScanStage.CAPTURED) Palette.Mint else Palette.Gold
    Canvas(Modifier.fillMaxSize()) {
        val c = 30.dp.toPx()
        val w = 3.5.dp.toPx()
        val stroke = StrokeCap.Round
        listOf(
            Offset(0f, 0f) to (1f to 1f),
            Offset(size.width, 0f) to (-1f to 1f),
            Offset(0f, size.height) to (1f to -1f),
            Offset(size.width, size.height) to (-1f to -1f),
        ).forEach { (o, d) ->
            drawLine(cornerColor, o, o + Offset(c * d.first, 0f), w, stroke)
            drawLine(cornerColor, o, o + Offset(0f, c * d.second), w, stroke)
        }
        if (stage == ScanStage.SCANNING) {
            val y = size.height * line
            drawLine(Palette.Gold, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
        }
    }
}

@Composable
private fun SuccessBadge() {
    Box(Modifier.size(58.dp).clip(CircleShape).background(Palette.Mint), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Check, "Berhasil", tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun CameraScaffold(title: String, onBack: () -> Unit, message: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Primary950).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            color = Color.White,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(12.dp),
        )
        Spacer(Modifier.weight(1f))
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CameraTip(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 11.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).border(4.dp, if (enabled) Palette.Gold else Color.Gray, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Potret KTP", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (enabled) Color.White else Color.Gray))
    }
}

@Composable
fun KycReviewScreen(signup: SignupDraft, kyc: KycDraft, onBack: () -> Unit, onRescan: () -> Unit, onNext: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(signup.name.ifBlank { "Pengguna" }) }
    var nik by rememberSaveable { mutableStateOf("3173012345670012") }
    var dobDigits by rememberSaveable { mutableStateOf(signup.dob.ifBlank { "14/03/1992" }.filter { it.isDigit() }) }
    val dob = formatDateDigits(dobDigits)
    var address by rememberSaveable { mutableStateOf("Jl. Merdeka No. 1, Jakarta Barat") }
    val today = LocalDate.now()
    val dobValid = Rules.isAdult(dob, today.year, today.monthValue, today.dayOfMonth)
    fun normalized(value: String) = value.trim().split(Regex("\\s+")).joinToString(" ").uppercase()
    val mismatch = (signup.name.isNotBlank() && normalized(name) != normalized(signup.name)) ||
        (signup.dob.isNotBlank() && dob != signup.dob)
    val valid = Rules.isValidFullName(name) && Rules.isValidNik(nik) && dobValid && address.isNotBlank() && !mismatch
    FormScreen(
        "Data diri",
        onBack,
        step = 3 to STEPS,
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Sebelumnya", onBack, modifier = Modifier.weight(1f))
                PrimaryButton("Selanjutnya", { kyc.name = name.trim(); onNext() }, modifier = Modifier.weight(1f), enabled = valid)
            }
        },
    ) {
        Headline(
            "Periksa data e-KTP",
            "Data di bawah terisi otomatis dari foto e-KTP. Koreksi jika ada yang kurang tepat.",
            titleColor = Palette.Primary,
        )
        Spacer(Modifier.height(14.dp))

        // Foto e-KTP · Data terbaca card (matching 09 Form Data Diri prototype)
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(56.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("e-KTP", color = Palette.Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Foto e-KTP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.Ink)
                    Text("✓ Data terbaca", color = Palette.Good, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                }
                OutlinedButton(
                    onClick = onRescan,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Palette.Primary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Foto ulang", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Primary)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (mismatch) {
            StatusChip("KTP tidak cocok dengan akun Anda", ChipTone.CRITICAL)
            Spacer(Modifier.height(6.dp))
            Text("Nama dan tanggal lahir harus sama dengan saat mendaftar.", color = Palette.InkSoft, fontSize = 12.5.sp)
            Spacer(Modifier.height(8.dp))
        } else {
            StatusChip("Cocok dengan data akun", ChipTone.GOOD)
            Spacer(Modifier.height(8.dp))
        }

        FormField("NIK (16 digit)", nik, { nik = it.filter { c -> c.isDigit() }.take(16) }, keyboardType = KeyboardType.Number, error = if (nik.isNotEmpty() && !Rules.isValidNik(nik)) "NIK harus 16 angka." else null)
        Spacer(Modifier.height(12.dp))
        FormField("Nama lengkap (sesuai e-KTP)", name, { name = it }, error = if (!Rules.isValidFullName(name)) "Masukkan nama lengkap." else null)
        Spacer(Modifier.height(12.dp))
        FormField("Tanggal lahir", dobDigits, { dobDigits = it.filter { c -> c.isDigit() }.take(8) }, keyboardType = KeyboardType.Number, transformation = DateTransformation, error = if (dob.length == 10 && !dobValid) "Tanggal tidak valid atau usia di bawah 18 tahun." else null)
        Spacer(Modifier.height(12.dp))
        FormField("Alamat sesuai e-KTP", address, { address = it }, singleLine = false)
    }
}

@ScreenPreview
@Composable
private fun KycReviewPreview() = ReidigTheme { KycReviewScreen(previewSignup(), previewKyc(), {}, {}, {}) }

@Composable
fun KycLivenessIntroScreen(onBack: () -> Unit, onStart: () -> Unit) {
    FormScreen(
        "Verifikasi wajah",
        onBack,
        step = 4 to STEPS,
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Sebelumnya", onBack, modifier = Modifier.weight(1f))
                PrimaryButton("Selanjutnya", onStart, modifier = Modifier.weight(1f))
            }
        },
    ) {
        Spacer(Modifier.height(12.dp))
        // Concentric rings + 96dp purple squircle illustration matching 10 Liveness Intro prototype
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(196.dp).clip(CircleShape).background(Palette.Primary50))
            Canvas(Modifier.size(148.dp)) {
                drawCircle(
                    color = Palette.Primary200,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                )
            }
            Box(
                Modifier.size(96.dp)
                    .shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = Palette.Primary, spotColor = Palette.Primary)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Palette.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CenterFocusStrong, null, tint = Palette.Gold, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Headline(
            "Verifikasi wajah",
            "Kami perlu memastikan kamu adalah pemilik asli e-KTP yang didaftarkan.",
            centered = true,
            titleColor = Palette.Primary,
        )
        Spacer(Modifier.height(18.dp))
        SurfaceCard {
            val tips = listOf(
                "Lepas kacamata hitam, masker, atau topi",
                "Pastikan wajah mendapat cahaya merata",
                "Ikuti instruksi gerakan (kedip / tengok)",
            )
            tips.forEachIndexed { i, tip ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.Primary50), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, tint = Palette.Primary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(tip, fontSize = 13.sp, color = Palette.InkSecondary)
                }
                if (i < tips.lastIndex) {
                    HorizontalDivider(color = Palette.Line.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@ScreenPreview
@Composable
private fun KycLivenessIntroPreview() = ReidigTheme { KycLivenessIntroScreen({}, {}) }

@Composable
fun KycFaceScanScreen(onBack: () -> Unit, onVerified: () -> Unit) {
    CameraPermissionGate("Verifikasi wajah", "Izinkan kamera untuk memindai wajah", onBack) {
        FaceCamera(onBack, onVerified)
    }
}

@ScreenPreview
@Composable
private fun KycFaceScanPreview() = ReidigTheme { KycFaceScanScreen({}, {}) }

@Composable
private fun FaceCamera(onBack: () -> Unit, onVerified: () -> Unit) {
    val prompts = listOf("Posisikan wajah di dalam bingkai", "Kedipkan mata perlahan", "Tahan sebentar...", "Wajah terverifikasi!")
    var index by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val previewView = rememberBoundPreview(
        CameraSelector.DEFAULT_FRONT_CAMERA,
        onReady = { ready = true },
        onError = { error = "Kamera depan tidak bisa dibuka." },
    )
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        while (index < prompts.lastIndex) {
            delay(1600)
            index++
        }
        delay(800)
        onVerified()
    }
    val done = index == prompts.lastIndex
    CameraScaffold("Verifikasi wajah", onBack, error ?: prompts[index]) {
        Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
            CameraSurface(previewView, Modifier.fillMaxSize().padding(horizontal = 30.dp).clip(RoundedCornerShape(50)))
            Canvas(Modifier.fillMaxSize()) {
                val rect = Rect(Offset(size.width * 0.12f, 0f), Size(size.width * 0.76f, size.height))
                drawOval(Color.White.copy(alpha = 0.25f), rect.topLeft, rect.size, style = Stroke(4.dp.toPx()))
                drawArc(
                    if (done) Palette.Mint else Palette.Gold,
                    -90f,
                    360f * (index + 1) / prompts.size,
                    false,
                    rect.topLeft,
                    rect.size,
                    style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            if (done) SuccessBadge()
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(prompts.size) { i ->
                Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= index) Palette.Gold else Color.White.copy(alpha = 0.25f)))
            }
        }
    }
}

@Composable
fun KycPinScreen(env: AppEnv, kyc: KycDraft, onBack: () -> Unit, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val confirming = first != null

    fun submit(value: String) {
        when {
            !confirming && Rules.isWeakPin(value) -> { error = "PIN terlalu mudah ditebak. Hindari angka berulang atau berurutan."; code = "" }
            !confirming -> { first = value; code = ""; error = null }
            value != first -> { error = "PIN tidak sama. Coba lagi."; code = "" }
            else -> { env.repo.completeKyc(value, kyc.name); onDone() }
        }
    }

    FormScreen(
        "Buat PIN",
        { if (confirming) { first = null; code = ""; error = null } else onBack() },
        step = 5 to STEPS,
        bottom = {
            NumberPad(
                onDigit = {
                    if (code.length < 6) {
                        error = null
                        code += it
                        if (code.length == 6) submit(code)
                    }
                },
                onDelete = { code = code.dropLast(1) },
            )
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Headline(
            if (confirming) "Konfirmasi PIN" else "Buat 6 digit PIN",
            if (confirming) "Masukkan ulang 6 digit PIN yang baru kamu buat." else "PIN digunakan untuk mengotorisasi setiap transaksi emas dan penarikan dana.",
            titleColor = Palette.Primary,
        )
        Spacer(Modifier.height(28.dp))
        CodeBoxes(code = code, masked = true, error = error != null)
        Spacer(Modifier.height(14.dp))
        if (error != null) {
            ErrorLine(error!!, Modifier.align(Alignment.CenterHorizontally))
        } else {
            Text(
                "Hindari tanggal lahir atau angka berulang (misal 111111).",
                color = Palette.InkSoft,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@ScreenPreview
@Composable
private fun KycPinPreview() = ReidigTheme { KycPinScreen(rememberPreviewEnv(false), previewKyc(), {}, {}) }

/** Layar 12 Selesai e-KYC sesuai desain prototipe Goldin v1. */
@Composable
fun KycVerifiedScreen(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        DoubleRingSuccessBadge(Icons.Outlined.Shield)
        Spacer(Modifier.height(24.dp))
        Text(
            "Verifikasi berhasil!",
            color = Palette.Primary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Identitasmu sudah terverifikasi dan akun dilindungi PIN. Sekarang kamu bisa transaksi emas penuh.",
            color = Palette.InkSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
        HeroPurpleCard(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Wallet ID", color = Palette.Primary200, fontSize = 12.sp)
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Palette.GoldSoft).padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text("Terenkripsi", color = Palette.GoldDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "GLD-8829-4410-24K",
                    color = Palette.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Mulai transaksi emas", onDone)
    }
}

@ScreenPreview
@Composable
private fun KycVerifiedPreview() = ReidigTheme { KycVerifiedScreen {} }

const val DEMO_PHONE_OTP = MockRepository.DEMO_PHONE_OTP
