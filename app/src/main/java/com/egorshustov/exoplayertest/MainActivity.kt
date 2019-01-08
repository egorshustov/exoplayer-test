package com.egorshustov.exoplayertest

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util


class MainActivity : AppCompatActivity() {

    private lateinit var player: SimpleExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        player = ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector())

        val playerView = findViewById<PlayerView>(R.id.player_view)
        playerView.player = player

        // Produces DataSource instances through which media data is loaded:
        val dataSourceFactory = DefaultDataSourceFactory(
            this,
            Util.getUserAgent(this, "ExoPlayerTest")
        )

        //val mediaUri = "http://stream.basso.fi:8000/stream"
        val mediaUri = "http://abclive.abcnews.com/i/abc_live4@136330/index_1200_av-b.m3u8?sd=10&b=1200&rebase=on"
        /*val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.parse(mediaUri))*/
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.parse(mediaUri))

        player.prepare(mediaSource)

        player.playWhenReady = true
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
