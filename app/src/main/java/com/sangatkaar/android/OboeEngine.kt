package com.sangatkaar.android

import android.content.res.AssetManager
import android.util.Log

data class MasterMeters(val inputPeak: Float, val outputPeak: Float, val gainReduction: Float)
data class SwarMeters(val peak: Float, val activeVoices: Int)
data class JivaOption(val name: String, val offset: Int)

object OboeEngine {
    private const val TAG = "OboeEngine"

    init {
        try {
            System.loadLibrary("sangatkaar")
        } catch (e: Throwable) {
            try {
                Log.w(TAG, "Native library 'sangatkaar' not found on classpath (JVM test environment).")
            } catch (_: Throwable) {}
        }
    }

    const val DEFAULT_SA_MIDI = 76
    const val SA_SOUNDING_OFFSET = -24
    const val DEFAULT_BPM = 90.0
    const val DEFAULT_VOLUME = 0.6666667f
    const val NONE_GLYPH = "–"
    const val SHRUTI_VOICE_COUNT = 4

    val SA_RANGE = 67..88
    val BPM_RANGE = 10.0..400.0
    val DEFAULT_PAN = floatArrayOf(-0.48f, 0.48f)

    const val SWAR_CELL_COUNT = 24
    const val SWAR_DEFAULT_BASE_TEMPO = 220.0
    const val SWAR_DEFAULT_SLOWDOWN = 0.74f
    const val SWAR_DEFAULT_SPEED_INDEX = 1
    const val SWAR_EMPTY = Int.MIN_VALUE
    const val SWAR_KEY_MIN = 43
    const val SWAR_KEY_MAX = 90
    const val SWAR_UP_CAP = 21

    val SWAR_SPEEDS = listOf(0.65, 0.8, 0.9, 1.0)
    val SWARA_NAMES = arrayOf("S", "r", "R", "g", "G", "m", "M", "P", "d", "D", "n", "N")
    val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val JIVA_INTERVALS = listOf(0, -1, -5, -7)

    val JIVA_OPTIONS = listOf(
        JivaOption("Pa", -5),
        JivaOption("Ma", -7),
        JivaOption("Ni", -1),
        JivaOption("Sa", 0),
        JivaOption("None", SWAR_EMPTY)
    )

    // JNI Native methods
    private external fun nativeLoad(assetManager: AssetManager): Boolean
    private external fun nativeStart(): Boolean
    private external fun nativeClose()
    private external fun nativeRequestStop()
    private external fun nativeInfo(): String
    private external fun nativeBeginFadeIn()
    private external fun nativeCompleteFadeIn()
    private external fun nativeHealth(): IntArray
    private external fun nativeClickWatch(watch: Boolean)
    private external fun nativeClickSnapshot(): FloatArray
    private external fun nativeClickStats(): FloatArray
    private external fun nativeCallbackLoad(): DoubleArray
    private external fun nativeMeterStart()
    private external fun nativeMeterStop()
    private external fun nativeMeterRead(): Double

    private external fun nativeSetMicroTuning(centsOffsets: FloatArray)

    private external fun nativeSetSa(saMidi: Int)
    private external fun nativeSetFineTune(cents: Double)
    private external fun nativeSetBpm(bpm: Double)
    private external fun nativeSetVolume(index: Int, volume: Float)
    private external fun nativeSetPan(index: Int, pan: Float)
    private external fun nativeSetTone(index: Int, tone: Float)
    private external fun nativeSetPower(index: Int, power: Boolean)
    private external fun nativeSetOctave(index: Int, octave: Int)
    private external fun nativeSetShrutiMode(enabled: Boolean)

    private external fun nativeSetJiva(index: Int, interval: Int, muted: Boolean)
    private external fun nativeSetFifth(index: Int, interval: Int, muted: Boolean)
    private external fun nativeSetJivaVariant(index: Int, variant: Int)
    private external fun nativeSetFifthVariant(index: Int, variant: Int)

    private external fun nativeSetStringVelocity(index: Int, stringIdx: Int, velocity: Int)
    private external fun nativeSetStringCents(index: Int, stringIdx: Int, cents: Double)
    private external fun nativeSetStringJavari(index: Int, stringIdx: Int, javari: Float)
    private external fun nativeAuditionString(index: Int, stringIdx: Int)

    // Master
    private external fun nativeMasterInputPeak(): Float
    private external fun nativeMasterOutputPeak(): Float
    private external fun nativeMasterGainReduction(): Float
    private external fun nativeSetMasterLevel(level: Float)

    // Tabla
    private external fun nativeTablaLoadSamples(assetManager: AssetManager, saMidi: Int, isSurTabla: Boolean): Boolean
    private external fun nativeTablaSetChati(assetManager: AssetManager, foldedSa: Int, isSur: Boolean): Boolean
    private external fun nativeTablaSetManjiraBank(assetManager: AssetManager, bank: Int): Boolean
    private external fun nativeTablaSetTaal(taalIndex: Int)
    private external fun nativeTablaSetVariation(variationIndex: Int)
    private external fun nativeTablaSetBpm(bpm: Double)
    private external fun nativeTablaSetFineTune(cents: Double)
    private external fun nativeTablaSetVolume(volume: Float)
    private external fun nativeTablaSetPan(pan: Float)
    private external fun nativeTablaSetTone(tone: Float)
    private external fun nativeTablaSetPower(power: Boolean)
    private external fun nativeTablaSetAutoVariation(enabled: Boolean)
    private external fun nativeTablaSetAutoHold(cycles: Int)
    private external fun nativeTablaSetManjiraEnabled(enabled: Boolean)
    private external fun nativeTablaSetManjiraVolume(volume: Float)
    private external fun nativeTablaBeginTable(ppq: Int, taalCount: Int)
    private external fun nativeTablaEndTable()
    private external fun nativeTablaAddPattern(patternIdx: Int, cycleTicks: Int, beats: Int, hasManjira: Boolean, rule1Max: Int, rule2Max: Int, bandCount: Int)
    private external fun nativeTablaAddBand(patternIdx: Int, bandIdx: Int, minBpm: Int, maxBpm: Int, variationCount: Int)
    private external fun nativeTablaAddEvents(patternIdx: Int, bandIdx: Int, variationIdx: Int, events: IntArray)
    private external fun nativeTablaTriggerPickup()
    private external fun nativeTablaFoldedSa(saMidi: Int): Int
    private external fun nativeTablaManjiraSemisFor(saMidi: Int): Int
    private external fun nativeTablaManjiraSemisNow(): Int
    private external fun nativeTablaCurrentBpm(): Double
    private external fun nativeTablaCurrentBand(): Int
    private external fun nativeTablaWantBand(): Int
    private external fun nativeTablaCurrentEventCount(): Int
    private external fun nativeTablaDipStarts(): Int
    private external fun nativeTablaDipCompletions(): Int
    private external fun nativeTablaLastDipReason(): Int
    private external fun nativeTablaLastQuantWait(): Int
    private external fun nativeTablaQuantTicks(): Int
    private external fun nativeTablaTakeOutPeak(): Float
    private external fun nativeTablaCycleCount(): Long
    private external fun nativeTablaCycleProgress(): Double
    private external fun nativeTablaLastCycleMicros(): Long
    private external fun nativeTablaLastCycleFrames(): Int
    private external fun nativeTablaLastTailSeconds(): Double
    private external fun nativeTablaPickupState(): Int
    private external fun nativeTablaHasPickup(patternIdx: Int, speed: Float): Boolean
    private external fun nativeTablaSetMetronome(clicks: Int, tempo: Double, level: Float)
    private external fun nativeTablaSetMetronomeLevel(level: Float)
    private external fun nativeTablaSetMetronomeMode(enabled: Boolean)
    private external fun nativeTablaSetMetronomeSignature(beats: Int, unit: Int)
    private external fun nativeTablaSetStartTick(tick: Double)
    private external fun nativeTablaTicksPerMatra(taalIndex: Int): Double

    // Swarmandal
    private external fun nativeSwarLoadSamples(assetManager: AssetManager): Boolean
    private external fun nativeSwarSetSa(saMidi: Int)
    private external fun nativeSwarSetFineTune(cents: Double)
    private external fun nativeSwarSetCell(index: Int, interval: Int, active: Boolean)
    private external fun nativeSwarSetCellVariant(index: Int, variant: Int)
    private external fun nativeSwarSetBaseTempo(bpm: Double)
    private external fun nativeSwarSetSlowdown(factor: Float)
    private external fun nativeSwarSetSpeedIndex(speedIndex: Int)
    private external fun nativeSwarSetVolume(volume: Float)
    private external fun nativeSwarSetPan(pan: Float)
    private external fun nativeSwarSetTone(tone: Float)
    private external fun nativeSwarSetLinear(linear: Boolean)
    private external fun nativeSwarSetRandomArmed(armed: Boolean)
    private external fun nativeSwarPlay()
    private external fun nativeSwarStop()
    private external fun nativeSwarPlayCells(cells: IntArray, variants: IntArray?)
    private external fun nativeSwarAudition(interval: Int, variant: Int)
    private external fun nativeSwarIsPlaying(): Boolean
    private external fun nativeSwarTakeOutPeak(): Float
    private external fun nativeSwarActiveVoices(): Int
    private external fun nativeSwarKeyForInterval(interval: Int): Int

    // Shruti Box
    private external fun nativeShrutiLoadSamples(assetManager: AssetManager): Boolean
    private external fun nativeShrutiSetFineTune(cents: Double)
    private external fun nativeShrutiSetPan(pan: Float)
    private external fun nativeShrutiSetPower(power: Boolean)
    private external fun nativeShrutiSetTone(tone: Float)
    private external fun nativeShrutiSetVoiceInterval(voiceIdx: Int, interval: Int)
    private external fun nativeShrutiSetVoiceLevel(voiceIdx: Int, level: Float)
    private external fun nativeShrutiSetVoicePan(voiceIdx: Int, pan: Float)
    private external fun nativeShrutiSetVoicePower(voiceIdx: Int, power: Boolean)
    private external fun nativeShrutiSetVolume(volume: Float)

    val isReady = java.util.concurrent.atomic.AtomicBoolean(false)

    fun setReady(ready: Boolean) {
        isReady.set(ready)
    }

    fun isReady(): Boolean = isReady.get()

    // Public wrappers
    fun load(assetManager: AssetManager): Boolean {
        val loaded = nativeLoad(assetManager)
        if (loaded) {
            isReady.set(true)
        }
        return loaded
    }

    fun start(): Boolean {
        if (!isReady.get()) {
            Log.w(TAG, "start() called before engine is ready")
            return false
        }
        return nativeStart()
    }

    fun close() {
        val wasReady = isReady.getAndSet(false)
        if (wasReady) {
            try {
                nativeClose()
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "nativeClose() unlinked: ${e.message}")
            }
        }
    }

    fun requestStop() {
        if (isReady.get()) {
            nativeRequestStop()
        }
    }

    fun info(): String {
        if (!isReady.get()) return "Engine not ready"
        return nativeInfo()
    }

    fun beginFadeIn() {
        if (!isReady.get()) {
            Log.w(TAG, "beginFadeIn() called before engine is ready")
            return
        }
        nativeBeginFadeIn()
    }

    fun setSa(saMidi: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "setSa() called before engine is ready")
            return
        }
        nativeSetSa(saMidi)
    }

    fun setFineTune(cents: Double) {
        if (!isReady.get()) {
            Log.w(TAG, "setFineTune() called before engine is ready")
            return
        }
        nativeSetFineTune(cents)
    }

    fun setBpm(bpm: Double) {
        if (!isReady.get()) {
            Log.w(TAG, "setBpm() called before engine is ready")
            return
        }
        nativeSetBpm(bpm)
    }

    fun setVolume(index: Int, volume: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "setVolume() called before engine is ready")
            return
        }
        nativeSetVolume(index, volume)
    }

    fun setPan(index: Int, pan: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "setPan() called before engine is ready")
            return
        }
        nativeSetPan(index, pan)
    }

    fun setTone(index: Int, tone: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "setTone() called before engine is ready")
            return
        }
        nativeSetTone(index, tone)
    }

    fun setPower(index: Int, power: Boolean) {
        if (!isReady.get()) {
            Log.w(TAG, "setPower() called before engine is ready")
            return
        }
        nativeSetPower(index, power)
    }

    fun setOctave(index: Int, octave: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "setOctave() called before engine is ready")
            return
        }
        nativeSetOctave(index, octave)
    }

    fun setShrutiMode(enabled: Boolean) {
        if (!isReady.get()) {
            Log.w(TAG, "setShrutiMode() called before engine is ready")
            return
        }
        nativeSetShrutiMode(enabled)
    }

    fun setJiva(index: Int, interval: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "setJiva() called before engine is ready")
            return
        }
        val muted = (interval == SWAR_EMPTY)
        nativeSetJiva(index, if (muted) 0 else interval, muted)
    }

    fun setFifth(index: Int, interval: Int?) {
        if (!isReady.get()) {
            Log.w(TAG, "setFifth() called before engine is ready")
            return
        }
        val muted = (interval == null)
        nativeSetFifth(index, interval ?: 0, muted)
    }

    fun setJivaVariant(index: Int, variant: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "setJivaVariant() called before engine is ready")
            return
        }
        nativeSetJivaVariant(index, variant)
    }

    fun setFifthVariant(index: Int, variant: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "setFifthVariant() called before engine is ready")
            return
        }
        nativeSetFifthVariant(index, variant)
    }

    fun setStringVelocity(index: Int, stringIdx: Int, velocity: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "setStringVelocity() called before engine is ready")
            return
        }
        nativeSetStringVelocity(index, stringIdx, velocity)
    }

    fun setStringCents(index: Int, stringIdx: Int, cents: Double) {
        if (!isReady.get()) {
            Log.w(TAG, "setStringCents() called before engine is ready")
            return
        }
        nativeSetStringCents(index, stringIdx, cents)
    }

    fun setStringJavari(index: Int, stringIdx: Int, javari: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "setStringJavari() called before engine is ready")
            return
        }
        nativeSetStringJavari(index, stringIdx, javari)
    }

    fun auditionString(index: Int, stringIdx: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "auditionString() called before engine is ready")
            return
        }
        nativeAuditionString(index, stringIdx)
    }

    fun masterMeters(): MasterMeters {
        if (!isReady.get()) return MasterMeters(0f, 0f, 0f)
        return MasterMeters(nativeMasterInputPeak(), nativeMasterOutputPeak(), nativeMasterGainReduction())
    }

    @Volatile var currentMasterLevel: Float = 1.0f
        private set

    fun setMasterLevel(level: Float) {
        currentMasterLevel = level
        if (!isReady.get()) {
            Log.w(TAG, "setMasterLevel() called before engine is ready")
            return
        }
        nativeSetMasterLevel(level)
    }

    // Tabla wrappers
    fun tablaLoadSamples(assetManager: AssetManager, saMidi: Int, isSurTabla: Boolean = false): Boolean {
        if (!isReady.get()) {
            Log.w(TAG, "tablaLoadSamples() called before engine is ready")
            return false
        }
        return nativeTablaLoadSamples(assetManager, saMidi, isSurTabla)
    }

    fun tablaCycleCount(): Long {
        if (!isReady.get()) return 0L
        return nativeTablaCycleCount()
    }

    fun tablaCycleProgress(): Double {
        if (!isReady.get()) return 0.0
        return nativeTablaCycleProgress()
    }

    fun tablaSetChati(assetManager: AssetManager, foldedSa: Int, isSur: Boolean): Boolean {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetChati() called before engine is ready")
            return false
        }
        return nativeTablaSetChati(assetManager, foldedSa, isSur)
    }

    fun tablaSetManjiraBank(assetManager: AssetManager, bank: Int): Boolean {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetManjiraBank() called before engine is ready")
            return false
        }
        return nativeTablaSetManjiraBank(assetManager, bank)
    }

    fun tablaSetTaal(taalIndex: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetTaal() called before engine is ready")
            return
        }
        nativeTablaSetTaal(taalIndex)
    }

    fun tablaSetVariation(variationIndex: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetVariation() called before engine is ready")
            return
        }
        nativeTablaSetVariation(variationIndex)
    }

    fun tablaSetBpm(bpm: Double) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetBpm() called before engine is ready")
            return
        }
        nativeTablaSetBpm(bpm)
    }

    fun tablaSetFineTune(cents: Double) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetFineTune() called before engine is ready")
            return
        }
        nativeTablaSetFineTune(cents)
    }

    fun tablaSetVolume(volume: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetVolume() called before engine is ready")
            return
        }
        nativeTablaSetVolume(volume)
    }

    fun tablaSetPan(pan: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetPan() called before engine is ready")
            return
        }
        nativeTablaSetPan(pan)
    }

    fun tablaSetTone(tone: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetTone() called before engine is ready")
            return
        }
        nativeTablaSetTone(tone)
    }

    fun tablaSetPower(power: Boolean) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetPower() called before engine is ready")
            return
        }
        nativeTablaSetPower(power)
    }

    fun tablaSetAutoVariation(enabled: Boolean) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetAutoVariation() called before engine is ready")
            return
        }
        nativeTablaSetAutoVariation(enabled)
    }

    fun tablaSetAutoHold(cycles: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetAutoHold() called before engine is ready")
            return
        }
        nativeTablaSetAutoHold(cycles)
    }

    fun tablaSetManjiraEnabled(enabled: Boolean) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetManjiraEnabled() called before engine is ready")
            return
        }
        nativeTablaSetManjiraEnabled(enabled)
    }

    fun tablaSetManjiraVolume(volume: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaSetManjiraVolume() called before engine is ready")
            return
        }
        nativeTablaSetManjiraVolume(volume)
    }

    fun tablaBeginTable(ppq: Int, taalCount: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaBeginTable() called before engine is ready")
            return
        }
        nativeTablaBeginTable(ppq, taalCount)
    }

    fun tablaEndTable() {
        if (!isReady.get()) {
            Log.w(TAG, "tablaEndTable() called before engine is ready")
            return
        }
        nativeTablaEndTable()
    }

    fun tablaAddPattern(patternIdx: Int, cycleTicks: Int, beats: Int, hasManjira: Boolean, rule1Max: Int, rule2Max: Int, bandCount: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaAddPattern() called before engine is ready")
            return
        }
        nativeTablaAddPattern(patternIdx, cycleTicks, beats, hasManjira, rule1Max, rule2Max, bandCount)
    }

    fun tablaAddBand(patternIdx: Int, bandIdx: Int, minBpm: Int, maxBpm: Int, variationCount: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaAddBand() called before engine is ready")
            return
        }
        nativeTablaAddBand(patternIdx, bandIdx, minBpm, maxBpm, variationCount)
    }

    fun tablaAddEvents(patternIdx: Int, bandIdx: Int, variationIdx: Int, events: IntArray) {
        if (!isReady.get()) {
            Log.w(TAG, "tablaAddEvents() called before engine is ready")
            return
        }
        nativeTablaAddEvents(patternIdx, bandIdx, variationIdx, events)
    }

    fun tablaTriggerPickup() {
        if (!isReady.get()) {
            Log.w(TAG, "tablaTriggerPickup() called before engine is ready")
            return
        }
        nativeTablaTriggerPickup()
    }

    fun tablaFoldedSa(saMidi: Int): Int {
        var s = saMidi
        while (s > 66) s -= 12
        while (s < 55) s += 12
        return s
    }

    fun tablaManjiraSemisFor(saMidi: Int): Int {
        return (((saMidi - 24) % 12) + 12) % 12
    }

    fun tablaManjiraSemisNow(): Int {
        if (!isReady.get()) return 0
        return nativeTablaManjiraSemisNow()
    }

    fun tablaLastTailSeconds(): Double {
        if (!isReady.get()) return 0.0
        return nativeTablaLastTailSeconds()
    }

    fun tablaPickupState(): Int {
        if (!isReady.get()) return 0
        return nativeTablaPickupState()
    }

    fun tablaHasPickup(patternIdx: Int, speed: Float): Boolean {
        if (!isReady.get()) return false
        return nativeTablaHasPickup(patternIdx, speed)
    }

    // Swarmandal wrappers
    fun swarLoadSamples(assetManager: AssetManager): Boolean {
        if (!isReady.get()) {
            Log.w(TAG, "swarLoadSamples() called before engine is ready")
            return false
        }
        return nativeSwarLoadSamples(assetManager)
    }

    fun swarSetSa(saMidi: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetSa() called before engine is ready")
            return
        }
        nativeSwarSetSa(saMidi)
    }

    fun swarSetFineTune(cents: Double) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetFineTune() called before engine is ready")
            return
        }
        nativeSwarSetFineTune(cents)
    }

    fun swarSetCell(index: Int, interval: Int?) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetCell() called before engine is ready")
            return
        }
        val active = (interval != null)
        nativeSwarSetCell(index, interval ?: 0, active)
    }

    fun swarSetCellVariant(index: Int, variant: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetCellVariant() called before engine is ready")
            return
        }
        nativeSwarSetCellVariant(index, variant)
    }

    fun swarSetBaseTempo(bpm: Double) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetBaseTempo() called before engine is ready")
            return
        }
        nativeSwarSetBaseTempo(bpm)
    }

    fun swarSetSlowdown(factor: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetSlowdown() called before engine is ready")
            return
        }
        nativeSwarSetSlowdown(factor)
    }

    fun swarSetSpeedIndex(speedIndex: Int) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetSpeedIndex() called before engine is ready")
            return
        }
        nativeSwarSetSpeedIndex(speedIndex)
    }

    fun swarSetVolume(volume: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetVolume() called before engine is ready")
            return
        }
        nativeSwarSetVolume(volume)
    }

    fun swarSetPan(pan: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetPan() called before engine is ready")
            return
        }
        nativeSwarSetPan(pan)
    }

    fun swarSetTone(tone: Float) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetTone() called before engine is ready")
            return
        }
        nativeSwarSetTone(tone)
    }

    fun swarSetLinear(linear: Boolean) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetLinear() called before engine is ready")
            return
        }
        nativeSwarSetLinear(linear)
    }

    fun swarSetRandomArmed(armed: Boolean) {
        if (!isReady.get()) {
            Log.w(TAG, "swarSetRandomArmed() called before engine is ready")
            return
        }
        nativeSwarSetRandomArmed(armed)
    }

    fun swarPlay() {
        if (!isReady.get()) {
            Log.w(TAG, "swarPlay() called before engine is ready")
            return
        }
        nativeSwarPlay()
    }

    fun swarStop() {
        if (!isReady.get()) {
            Log.w(TAG, "swarStop() called before engine is ready")
            return
        }
        nativeSwarStop()
    }

    fun swarPlayCells(cells: IntArray, variants: IntArray? = null) {
        if (!isReady.get()) {
            Log.w(TAG, "swarPlayCells() called before engine is ready")
            return
        }
        nativeSwarPlayCells(cells, variants)
    }

    fun swarAudition(interval: Int, variant: Int = 1) {
        if (!isReady.get()) {
            Log.w(TAG, "swarAudition() called before engine is ready")
            return
        }
        nativeSwarAudition(interval, variant)
    }

    fun swarIsPlaying(): Boolean {
        if (!isReady.get()) return false
        return nativeSwarIsPlaying()
    }

    fun swarMeters(): SwarMeters {
        if (!isReady.get()) return SwarMeters(0f, 0)
        return SwarMeters(nativeSwarTakeOutPeak(), nativeSwarActiveVoices())
    }

    fun swarKeyForInterval(interval: Int): Int {
        if (!isReady.get()) return interval
        return nativeSwarKeyForInterval(interval)
    }

    fun octaveMark(octave: Int): String = when {
        octave == 0 -> ""
        octave == 1 -> "."
        octave == -1 -> ","
        octave >= 2 -> ".."
        else -> ",,"
    }

    fun swaraLabel(interval: Int): String {
        val idx = ((interval % 12) + 12) % 12
        val oct = Math.floor(interval / 12.0).toInt()
        return SWARA_NAMES[idx] + octaveMark(oct)
    }

    fun fifthLabel(interval: Int): String {
        val idx = ((interval % 12) + 12) % 12
        val oct = Math.max(-1, Math.floor(interval / 12.0).toInt() + 1)
        return SWARA_NAMES[idx] + octaveMark(oct)
    }

    fun fifthIntervals(saMidi: Int): List<Int> {
        val list = mutableListOf<Int>()
        for (offset in -33..24) {
            val note = offset + saMidi
            if (note in 55..91) {
                list.add(offset)
            }
        }
        return list.sorted()
    }

    fun saDisplayName(saMidi: Int): String {
        val noteIdx = (((saMidi - 24) % 12) + 12) % 12
        val octave = ((saMidi - 24) / 12) - 1
        return "${NOTE_NAMES[noteIdx]}$octave"
    }

    fun swarLabel(interval: Int): String {
        return swaraLabel(interval + 12)
    }

    fun shrutiVariantCount(interval: Int): Int {
        val semitone = ((interval % 12) + 12) % 12
        return if (semitone == 0 || semitone == 7) 1 else 2
    }

    // Shruti Box public methods
    fun shrutiLoadSamples(assetManager: AssetManager): Boolean {
        if (!isReady.get()) return false
        return nativeShrutiLoadSamples(assetManager)
    }

    fun shrutiSetFineTune(cents: Double) {
        if (isReady.get()) nativeShrutiSetFineTune(cents)
    }

    fun shrutiSetPan(pan: Float) {
        if (isReady.get()) nativeShrutiSetPan(pan)
    }

    fun shrutiSetPower(power: Boolean) {
        if (isReady.get()) nativeShrutiSetPower(power)
    }

    fun shrutiSetTone(tone: Float) {
        if (isReady.get()) nativeShrutiSetTone(tone)
    }

    fun shrutiSetVoiceInterval(voiceIdx: Int, interval: Int) {
        if (isReady.get()) nativeShrutiSetVoiceInterval(voiceIdx, interval)
    }

    fun shrutiSetVoiceLevel(voiceIdx: Int, level: Float) {
        if (isReady.get()) nativeShrutiSetVoiceLevel(voiceIdx, level)
    }

    fun shrutiSetVoicePan(voiceIdx: Int, pan: Float) {
        if (isReady.get()) nativeShrutiSetVoicePan(voiceIdx, pan)
    }

    fun shrutiSetVoicePower(voiceIdx: Int, power: Boolean) {
        if (isReady.get()) nativeShrutiSetVoicePower(voiceIdx, power)
    }

    fun shrutiSetVolume(volume: Float) {
        if (isReady.get()) nativeShrutiSetVolume(volume)
    }

    // Microtuning public methods
    fun setMicroTuning(centsOffsets: FloatArray) {
        if (isReady.get()) nativeSetMicroTuning(centsOffsets)
    }

    // Metronome public methods
    fun tablaSetMetronome(clicks: Int, tempo: Double, level: Float) {
        if (isReady.get()) nativeTablaSetMetronome(clicks, tempo, level)
    }

    fun tablaSetMetronomeLevel(level: Float) {
        if (isReady.get()) nativeTablaSetMetronomeLevel(level)
    }

    fun tablaSetMetronomeMode(enabled: Boolean) {
        if (isReady.get()) nativeTablaSetMetronomeMode(enabled)
    }

    fun tablaSetMetronomeSignature(beats: Int, unit: Int) {
        if (isReady.get()) nativeTablaSetMetronomeSignature(beats, unit)
    }

    fun tablaSetStartTick(tick: Double) {
        if (isReady.get()) nativeTablaSetStartTick(tick)
    }

    fun tablaTicksPerMatra(taalIndex: Int): Double {
        if (!isReady.get()) return 480.0
        return nativeTablaTicksPerMatra(taalIndex)
    }

    // Diagnostics & Fade public methods
    fun completeFadeIn() {
        if (isReady.get()) nativeCompleteFadeIn()
    }

    fun health(): IntArray = if (isReady.get()) nativeHealth() else intArrayOf()
    fun clickWatch(watch: Boolean) { if (isReady.get()) nativeClickWatch(watch) }
    fun clickSnapshot(): FloatArray = if (isReady.get()) nativeClickSnapshot() else floatArrayOf()
    fun clickStats(): FloatArray = if (isReady.get()) nativeClickStats() else floatArrayOf()
    fun callbackLoad(): DoubleArray = if (isReady.get()) nativeCallbackLoad() else doubleArrayOf()
    fun meterStart() { if (isReady.get()) nativeMeterStart() }
    fun meterStop() { if (isReady.get()) nativeMeterStop() }
    fun meterRead(): Double = if (isReady.get()) nativeMeterRead() else 0.0
}
