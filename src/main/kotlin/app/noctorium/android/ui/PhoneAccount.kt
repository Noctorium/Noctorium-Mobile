package app.noctorium.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.account.NoctoriumUser
import app.noctorium.core.AppState
import app.noctorium.core.Destination
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** The listener's picture in a circle, on Home's title: the account's, or their initial, or an empty head when signed out. */
@Composable
internal fun AccountPicture(state: AppState, size: Dp, open: () -> Unit) {
    val user = state.account.collectAsState().value.user
    val circle = Modifier.size(size).clip(CircleShape).clickable(onClickLabel = "Account", onClick = open)
    when {
        user?.avatarUrl != null -> AsyncImage(user.avatarUrl, "Account", circle, contentScale = ContentScale.Crop)
        user != null -> Box(circle.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(user.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = (size.value * .42f).sp)
        }
        else -> Box(circle, contentAlignment = Alignment.Center) {
            Icon(Glyph.ACCOUNT_CIRCLE.image, "Account", Modifier.fillMaxSize(.8f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The Noctorium account as a screen of its own, opened from the picture on Home. */
@Composable
internal fun AccountScreen(state: AppState) {
    val account by state.account.collectAsState()
    ScreenScaffold {
        ScreenTitle("Account", close = { state.navigate(Destination.HOME) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            NoctoriumAccountCard(state)
            account.user?.let { PhoneProfileCards(state, it, account) }
        }
    }
}

/**
 * The website's Profile and danger zone: picture, name, email, password and deleting. A change of email, password or
 * deleting asks for the password (when the account has one) and the two-step code (when that is on), as the service does.
 */
@Composable
private fun PhoneProfileCards(state: AppState, user: NoctoriumUser, account: app.noctorium.core.NoctoriumAccountState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(user.displayName) { mutableStateOf(user.displayName) }
    var email by remember(user.email) { mutableStateOf(user.email) }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var proofPassword by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val busy = account.profileBusy
    val twoStep = user.twoStep.totp || user.twoStep.telegram
    fun proof() = buildMap {
        if (user.hasPassword) put("password", proofPassword)
        if (twoStep) put("code", code)
    }
    fun sent() { proofPassword = ""; code = ""; newPassword = ""; confirm = "" }
    // Android's own photo picker: no permission asked for, and only the one picture chosen is lent.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.let(::squareJpeg) }.getOrNull()
            }
            if (jpeg != null) state.uploadNoctoriumAvatar(jpeg)
        }
    }

    SettingsCardShell {
        CardHeading(Glyph.ACCOUNT_CIRCLE.image, "Profile")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountPicture(state, 64.dp) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Spacer(Modifier.width(12.dp))
            Column {
                SkinnedTextButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) {
                    Text("Choose a picture")
                }
                if (user.linked.discord) SkinnedTextButton({ state.changeNoctoriumProfile("avatar-discord") }, enabled = !busy) { Text("Use Discord's") }
                if (user.linked.telegram) SkinnedTextButton({ state.changeNoctoriumProfile("avatar-telegram") }, enabled = !busy) { Text("Use Telegram's") }
                if (user.avatarUrl != null) SkinnedTextButton({ state.changeNoctoriumProfile("avatar-remove") }, enabled = !busy) { Text("Remove") }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SkinnedButton({ state.changeNoctoriumProfile("name", mapOf("name" to name)) }, enabled = !busy && name.isNotBlank() && name != user.displayName) {
            Text("Save name")
        }
    }
    SettingsCardShell {
        CardHeading(Glyph.LOCK.image, "Email and password")
        Text(
            "Changing either signs your other devices out. This phone stays signed in.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (user.hasPassword) {
            OutlinedTextField(
                proofPassword, { proofPassword = it }, label = { Text("Current password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }
        if (twoStep) {
            OutlinedTextField(
                code, { code = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (user.twoStep.totp) "Code from your authenticator app" else "Code from Telegram") },
            )
            if (user.twoStep.telegram) SkinnedTextButton(state::sendNoctoriumProfileTelegramCode) { Text("Send me a code on Telegram") }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SkinnedButton({ state.changeNoctoriumProfile("email", proof() + ("email" to email)); sent() }, enabled = !busy && email.isNotBlank() && email != user.email) {
            Text("Change email")
        }
        if (user.hasPassword) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                newPassword, { newPassword = it }, label = { Text("New password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), supportingText = { Text("At least 10 characters.") },
                modifier = Modifier.fillMaxWidth(),
            )
            SkinnedButton(
                {
                    state.changeNoctoriumProfile("password", mapOf("current" to proofPassword, "password" to newPassword) + (if (twoStep) mapOf("code" to code) else emptyMap()))
                    sent()
                },
                enabled = !busy && newPassword.isNotBlank() && proofPassword.isNotBlank(),
            ) { Text("Change password") }
        }
    }
    SettingsCardShell {
        CardHeading(Glyph.DELETE.image, "Delete the account")
        Text(
            "Deletes the account and everything it counted, for good. Type \"delete my account\"; the password and code above prove it is you.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(confirm, { confirm = it }, label = { Text("delete my account") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SkinnedButton(
            { state.changeNoctoriumProfile("delete", proof() + ("confirm" to confirm)); sent() },
            enabled = !busy && confirm.trim().equals("delete my account", ignoreCase = true),
        ) { Text("Delete my account") }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
    account.profileError?.let { message ->
        SettingsCardShell {
            Text(message, fontSize = 12.sp)
            SkinnedTextButton(state::clearNoctoriumProfileError) { Text("OK") }
        }
    }
}

/** The middle square of [bitmap], at 256 pixels, as a JPEG: what the account service keeps. */
private fun squareJpeg(bitmap: Bitmap): ByteArray {
    val side = minOf(bitmap.width, bitmap.height)
    val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
    return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
}
