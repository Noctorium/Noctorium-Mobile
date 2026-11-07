package app.noctorium.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.noctorium.playlists.NoctoriumPlaylistFile
import app.noctorium.playlists.PlaylistCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Noctorium playlists in a folder the phone's own file picker chose (the Storage Access Framework): the phone's
 * storage, an SD card, a folder Syncthing or a cloud app keeps in step -- wherever the picker reaches. [tree] is the
 * picker's address for the folder, whose permission was kept when it was chosen; the playlists go in a folder
 * called "Noctorium Playlists" inside it, a file each, exactly as a computer's synced folder has them.
 */
class SafPlaylistCloud(private val context: Context, private val tree: Uri) : PlaylistCloud {
    private val resolver get() = context.contentResolver

    private fun root(): DocumentFile = DocumentFile.fromTreeUri(context, tree)?.takeIf { it.canRead() }
        ?: error("The folder chosen for Noctorium playlists can no longer be reached. Choose it again in Settings.")

    private fun folder(create: Boolean): DocumentFile? {
        val root = root()
        root.findFile(NoctoriumPlaylistFile.FOLDER)?.takeIf { it.isDirectory }?.let { return it }
        return if (create) root.createDirectory(NoctoriumPlaylistFile.FOLDER) ?: error("Could not make a folder for the playlists there.") else null
    }

    override val description: String
        get() = runCatching { root().name }.getOrNull()?.let { "$it › ${NoctoriumPlaylistFile.FOLDER}" } ?: "A folder on this phone"

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        folder(create = false)?.listFiles()?.mapNotNull { it.name }.orEmpty()
    }

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        val file = folder(create = false)?.findFile(name) ?: return@withContext null
        resolver.openInputStream(file.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override suspend fun write(name: String, text: String) = withContext(Dispatchers.IO) {
        val folder = folder(create = true)!!
        // Made as a plain file of bytes, so no provider adds an extension of its own to the name.
        val file = folder.findFile(name) ?: folder.createFile("application/octet-stream", name)
            ?: error("Could not write the playlist $name there.")
        // "wt": written over from the start, never appended to the copy that was there.
        resolver.openOutputStream(file.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not write the playlist $name there.")
        Unit
    }
}
