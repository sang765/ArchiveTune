package moe.rukamori.archivetune.spotify

import android.content.Context
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.player.Player
import xyz.gianlu.librespot.player.PlayerConfiguration
import xyz.gianlu.librespot.player.PlayerConfiguration.AudioOutput
import java.io.Closeable

class SpotifyDirectPlayer(
    context: Context,
) : Closeable {
    private var session: Session? = null
    private var player: Player? = null

    suspend fun connect(
        username: String,
        password: String,
    ): Result<Unit> =
        runCatching {
            val conf =
                PlayerConfiguration.Builder()
                    .setOutput(AudioOutput.DEBUG)
                    .build()

            session =
                Session.Builder(conf)
                    .userPass(username, password)
                    .build()

            player = Player(conf, session!!)
        }

    fun play(uri: String) {
        player?.load(uri, true, false)
    }

    override fun close() {
        player?.close()
        session?.close()
    }
}
