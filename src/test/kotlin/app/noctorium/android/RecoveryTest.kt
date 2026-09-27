package app.noctorium.android

import androidx.media3.common.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which ways a stream can fail are worth fetching a fresh address for, and when.
 *
 * A dropped connection -- the commonest way a song stops on a phone that moves -- used to go straight to
 * an error, because only a refused stream was ever retried. And the reverse matters as much: retrying a
 * failure that will fail the same way again only delays the message by the length of the retry.
 */
class RecoveryTest {

    @Test
    fun `a refused or changed stream is fetched again at once`() {
        assertEquals(Recovery.NOW, recoveryFor(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertEquals(Recovery.NOW, recoveryFor(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE))
    }

    /** Resolving needs the network too, so a connection that dropped waits for it to come back first. */
    @Test
    fun `a dropped connection waits for the network, then comes back`() {
        assertEquals(Recovery.AFTER_NETWORK, recoveryFor(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(Recovery.AFTER_NETWORK, recoveryFor(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
    }

    @Test
    fun `a failure that would fail the same way again is reported, not retried`() {
        listOf(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        ).forEach { assertNull(recoveryFor(it), "code $it should not be retried") }
    }

    @Test
    fun `recovery is bounded`() {
        assertEquals(2, MAX_RECOVERIES)
    }
}
