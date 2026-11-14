package io.booup.roadmate

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

/** Self-made cues from tools/sfx/generate_sfx.py. Volume follows the system sonification stream. */
internal enum class RoadmateSound(val res: Int, val volume: Float) {
    TAP(R.raw.ui_tap, .7f), LOCKED(R.raw.ui_locked, .8f), REWARD(R.raw.reward_claim, .9f),
    UPGRADE(R.raw.pass_upgrade, 1f), POSTCARD(R.raw.postcard_open, .9f),
}

/** Silent until [init], so JVM tests and previews never touch audio. */
internal object RoadmateSounds {
    private var pool: SoundPool? = null
    private val ids = IntArray(RoadmateSound.entries.size)

    fun init(context: Context) {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build().also { sounds ->
            RoadmateSound.entries.forEach { ids[it.ordinal] = sounds.load(context, it.res, 1) }
        }
    }

    fun play(sound: RoadmateSound) {
        // A slight pitch drift keeps repeated taps from sounding mechanical.
        val rate = if (sound == RoadmateSound.TAP) .96f + Random.nextFloat() * .08f else 1f
        pool?.play(ids[sound.ordinal], sound.volume, sound.volume, 1, 0, rate)
    }
}

/** Plays [sound] only when the game action actually saved. */
internal fun Boolean.cue(sound: RoadmateSound) = also { if (it) RoadmateSounds.play(sound) }
