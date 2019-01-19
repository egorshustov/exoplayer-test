package com.egorshustov.exoplayertest

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util


class MainActivity : AppCompatActivity() {

    private lateinit var player: SimpleExoPlayer
    private lateinit var exoPlayerView: ZoomableExoPlayerView
    private val mediaUri = "https://abclive1-lh.akamaihd.net/i/abc_live01@423395/master.m3u8"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exoPlayerView = findViewById(R.id.exo_player_view)
        player = ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector())

        exoPlayerView.player = player

        //exoPlayerView.setUseController(false) // or app:use_controller="false" in .xml file

        // Produces DataSource instances through which media data is loaded:
        val dataSourceFactory = DefaultDataSourceFactory(
            this,
            Util.getUserAgent(this, "ExoPlayerTest")
        )

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.parse(mediaUri))

        player.prepare(mediaSource)
        player.playWhenReady = true
    }

    fun stopOrPlay(v: View) {
        player.playWhenReady = !player.playWhenReady
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
