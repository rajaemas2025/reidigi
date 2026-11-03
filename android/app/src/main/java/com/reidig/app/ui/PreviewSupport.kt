package com.reidig.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.reidig.shared.GoldToken
import com.reidig.shared.MockRepository


/** Ukuran layar sesuai desain, 390 × 844. */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
annotation class ScreenPreview

@Composable
internal fun rememberPreviewEnv(verified: Boolean = true): AppEnv {
    val scope = rememberCoroutineScope()
    return remember {
        val repo = MockRepository(clock = System::currentTimeMillis)
        if (verified) repo.login("pengguna@gmail.com", "Rahasia123") else repo.register("Sari Dewi", "sari@gmail.com")
        AppEnv(repo, SnackbarHostState(), scope)
    }
}

internal fun previewSignup() = SignupDraft().apply {
    email = "sari@gmail.com"
    name = "Sari Dewi"
    dob = "14/03/1992"
}

internal fun previewKyc() = KycDraft().apply { phone = "81234567890" }

internal val AppEnv.firstToken: GoldToken get() = repo.state.value.tokens.first()
