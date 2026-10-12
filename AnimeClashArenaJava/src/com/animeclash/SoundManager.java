package com.animeclash;

import javax.sound.sampled.*;
import javax.sound.midi.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SoundManager — Quản lý toàn bộ âm thanh game
 *
 * ● Nhạc nền BGM (MIDI synthesizer — không cần file ngoài)
 * ● SFX (sinh PCM trực tiếp bằng code — không cần file ngoài)
 *
 * Gọi SoundManager.init() trước khi dùng.
 */
public class SoundManager {

    // ─── Volume controls ────────────────────────────────
    private static float sfxVolume  = 0.7f;
    private static float bgmVolume  = 0.5f;
    private static boolean muted    = false;

    // ─── MIDI BGM ────────────────────────────────────────
    private static Sequencer   sequencer;
    private static Synthesizer synthesizer;

    // ─── Thread pool cho SFX (tránh block main thread) ──
    private static final ExecutorService sfxPool =
        Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "SFX-Thread");
            t.setDaemon(true);
            return t;
        });

    // ─── Trạng thái nhạc nền ────────────────────────────
    private static boolean bgmPlaying = false;

    // ================================================================
    //  INIT
    // ================================================================
    public static void init() {
        try {
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            sequencer   = MidiSystem.getSequencer(false);
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
            System.out.println("[SoundManager] MIDI ready.");
        } catch (Exception e) {
            System.err.println("[SoundManager] MIDI init failed: " + e.getMessage());
        }
    }

    public static void dispose() {
        stopBGM();
        sfxPool.shutdownNow();
        if (sequencer   != null && sequencer.isOpen())   sequencer.close();
        if (synthesizer != null && synthesizer.isOpen()) synthesizer.close();
    }

    // ================================================================
    //  BGM — MIDI Background Music
    // ================================================================

    /** Phát nhạc nền trận đấu (battle theme) */
    public static void playBattleBGM() {
        if (muted) return;
        sfxPool.submit(() -> {
            try {
                stopBGM();
                Sequence seq = buildBattleSequence();
                sequencer.setSequence(seq);
                sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                sequencer.setTempoInBPM(148);
                sequencer.start();
                bgmPlaying = true;
            } catch (Exception e) {
                System.err.println("[BGM] " + e.getMessage());
            }
        });
    }

    /** Phát nhạc nền màn title (calm/epic) */
    public static void playTitleBGM() {
        if (muted) return;
        sfxPool.submit(() -> {
            try {
                stopBGM();
                Sequence seq = buildTitleSequence();
                sequencer.setSequence(seq);
                sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                sequencer.setTempoInBPM(88);
                sequencer.start();
                bgmPlaying = true;
            } catch (Exception e) {
                System.err.println("[BGM] " + e.getMessage());
            }
        });
    }

    public static void stopBGM() {
        if (sequencer != null && sequencer.isRunning()) {
            sequencer.stop();
            bgmPlaying = false;
        }
    }

    // ================================================================
    //  SFX — Sinh PCM không cần file ngoài
    // ================================================================

    public static void playHit()        { if (!muted) sfxPool.submit(() -> playSFX(genHit())); }
    public static void playSkill()      { if (!muted) sfxPool.submit(() -> playSFX(genSkill(0), true)); }
    public static void playSkill(int charID) { if (!muted) sfxPool.submit(() -> playSFX(genSkill(charID), true)); }
    public static void playUltimate()   { if (!muted) sfxPool.submit(() -> playSFX(genUltimate(0), true)); }
    public static void playUltimate(int charID) { if (!muted) sfxPool.submit(() -> playSFX(genUltimate(charID), true)); }
    public static void playKO()         { if (!muted) sfxPool.submit(() -> playSFX(genKO())); }
    public static void playRoundStart() { if (!muted) sfxPool.submit(() -> playSFX(genRoundStart())); }
    public static void playRoundEnd()   { if (!muted) sfxPool.submit(() -> playSFX(genRoundEnd())); }
    public static void playCountdown()  { if (!muted) sfxPool.submit(() -> playSFX(genBeep(880, 0.08f))); }
    public static void playScoreCount() { if (!muted) sfxPool.submit(() -> playSFX(genBeep(1200, 0.03f))); }
    public static void playRankStamp()  { if (!muted) sfxPool.submit(() -> playSFX(genRankStamp())); }
    public static void playDash()       { if (!muted) sfxPool.submit(() -> playSFX(genDash())); }
    public static void playJump()       { if (!muted) sfxPool.submit(() -> playSFX(genJump())); }

    public static void toggleMute() { muted = !muted; if (muted) stopBGM(); }
    public static boolean isMuted() { return muted; }

    // ================================================================
    //  PCM generators — tạo âm thanh bằng toán học
    // ================================================================

    private static final int SAMPLE_RATE = 44100;

    private static byte[] genHit() {
        // Tiếng đánh CỰC LỰC: Initial snap mạnh + Heavy White Noise Burst + Low Bass Drop chấn động
        int len = (int)(SAMPLE_RATE * 0.18f);
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            float t = (float) i / SAMPLE_RATE;
            // Decay nhanh cho snap ban đầu, decay vừa cho tiếng body
            float snapEnv = (float) Math.exp(-t * 60);
            float bodyEnv = (float) Math.exp(-t * 22);
            
            // White noise (Impact sỏi đá/va chạm)
            float noise = (float)(Math.random() * 2 - 1);
            
            // Bass drop từ 300Hz xuống 50Hz (tạo độ "lực" và đầm)
            float freq  = 300f - 250f * (t / 0.18f);
            float tone  = (float) Math.sin(2 * Math.PI * freq * t);
            
            // Overdrive distortion nhẹ để nghe giòn hơn
            float sample = (noise * 0.5f + tone * 0.5f) * bodyEnv + (noise * snapEnv * 0.8f);
            sample = (float) Math.tanh(sample * 1.8f); // Soft clipping distortion
            
            buf[i] = (byte)(sample * 120 * sfxVolume);
        }
        return buf;
    }

    private static byte[] genSkill(int charID) {
        // Tiếng chiêu ĐẶC TRƯNG CHO TỪNG NHÂN VẬT (Khuếch đại biên độ tối đa 127 để cực TO và LỰC)
        int len = (int)(SAMPLE_RATE * 0.45f);
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            float t   = (float) i / SAMPLE_RATE;
            float env = (float)(Math.exp(-t * 5) * (t < 0.03f ? t / 0.03f : 1f));
            float sample = 0;
            
            switch (charID) {
                case 0 -> {
                    // Naru (Gió / Vòng xoáy): Rising whoosh + fast modulation
                    float freq = 200 + 1400 * t;
                    float mod  = (float) Math.sin(2 * Math.PI * 45 * t); // Wind flutter
                    sample = (float) Math.sin(2 * Math.PI * freq * t + mod) * 0.7f + (float)(Math.random()*2-1)*0.3f;
                }
                case 1 -> {
                    // Gokai (Rìu lửa / Cuồng nộ): Deep growl + heavy fire crackle
                    float freq = 80 + 150 * t;
                    float tone = (float) Math.sin(2 * Math.PI * freq * t);
                    float noise = (float)(Math.random() * 2 - 1);
                    sample = (float) Math.tanh((tone * 0.7f + noise * 0.5f) * 2.5f); // heavy distorted impact
                }
                case 2 -> {
                    // Kira (Phép / Điện từ): Futuristic FM Laser sweep
                    float freq = 1800 - 2500 * t;
                    float fm   = (float) Math.sin(2 * Math.PI * 300 * t);
                    sample = (float) Math.sin(2 * Math.PI * Math.max(100, freq) * t + fm * 2f);
                }
                case 3 -> {
                    // Zin (Kiếm / Chớp): High-pitch razor metallic slice + electric spark
                    float freq = 2200 - 800 * t;
                    float ring = (float) Math.sin(2 * Math.PI * freq * t);
                    float spark = (Math.random() < 0.3) ? (float)(Math.random()*2-1) : 0f;
                    sample = ring * 0.75f + spark * 0.35f;
                }
                default -> {
                    float freq = 300 + 1200 * t;
                    sample = (float) Math.sin(2 * Math.PI * freq * t);
                }
            }
            
            // Khuếch đại tối đa biên độ của byte PCM (-127 tới 127)
            buf[i] = (byte)(Math.max(-127, Math.min(127, sample * env * 127)));
        }
        return buf;
    }

    private static byte[] genUltimate(int charID) {
        // Tiếng Ultimate BÙNG NỔ CHO TỪNG NHÂN VẬT (Khuếch đại biên độ tối đa 127 để cực TO và LỰC)
        int len = (int)(SAMPLE_RATE * 1.4f);
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            float t   = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * 3.0f);
            float sample = 0;
            
            // Bass nền chung tạo rung chấn cho mọi Ultimate
            float subBass = (float) Math.sin(2 * Math.PI * (55 - 20 * t) * t) * 0.4f;
            
            switch (charID) {
                case 0 -> {
                    // Naru (Đại Long Xoáy): Epic rising storm
                    float wind = (float) Math.sin(2 * Math.PI * (300 + 800 * t) * t) * (float) Math.sin(2 * Math.PI * 35 * t);
                    sample = subBass + wind * 0.5f + (float)(Math.random()*2-1)*0.3f;
                }
                case 1 -> {
                    // Gokai (Vô Cực Hỏa): Magma explosion + massive distorted thud
                    float thud = (float) Math.sin(2 * Math.PI * 110 * t) * (float) Math.exp(-t * 12);
                    float crackle = (float)(Math.random() * 2 - 1) * (float) Math.exp(-t * 4);
                    sample = (float) Math.tanh((subBass + thud + crackle * 0.6f) * 3.0f);
                }
                case 2 -> {
                    // Kira (Hủy Diệt Điện Từ): Heavy particle cannon charge & laser beam
                    float charge = (float) Math.sin(2 * Math.PI * (150 + 2000 * t) * t) * (t < 0.2f ? t/0.2f : 1f);
                    float beam = (float) Math.sin(2 * Math.PI * (800 + (Math.sin(t*50)*100)) * t);
                    sample = subBass + charge * 0.4f + beam * 0.4f;
                }
                case 3 -> {
                    // Zin (Bão Kiếm): Thousands of metallic blade slices echoing
                    float blade1 = (float) Math.sin(2 * Math.PI * 1800 * t);
                    float blade2 = (float) Math.sin(2 * Math.PI * 2400 * t) * (float) Math.sin(2 * Math.PI * 60 * t);
                    float noise = (float)(Math.random()*2-1) * 0.25f;
                    sample = subBass + blade1 * 0.35f + blade2 * 0.35f + noise;
                }
                default -> {
                    float low  = (float) Math.sin(2 * Math.PI * 60  * t) * 0.5f;
                    float mid  = (float) Math.sin(2 * Math.PI * 220 * t) * 0.3f;
                    sample = low + mid;
                }
            }
            
            // Khuếch đại tối đa biên độ của byte PCM (-127 tới 127)
            buf[i] = (byte)(Math.max(-127, Math.min(127, sample * env * 127)));
        }
        return buf;
    }

    private static byte[] genKO() {
        // Tiếng KO: low thud + descending wail
        int len = (int)(SAMPLE_RATE * 1.5f);
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            float t   = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * 2.5f);
            float freq = 440 * (float) Math.exp(-t * 2);
            float wave = (float) Math.sin(2 * Math.PI * freq * t);
            float thud = (float)(Math.random() * 2 - 1) * (float) Math.exp(-t * 20) * 0.5f;
            buf[i] = (byte)((wave * 0.7f + thud) * env * 100 * sfxVolume);
        }
        return buf;
    }

    private static byte[] genRoundStart() {
        // 3 beep tăng dần
        int beepLen = (int)(SAMPLE_RATE * 0.12f);
        int totalLen = beepLen * 4;
        byte[] buf = new byte[totalLen];
        float[] freqs = {440f, 550f, 660f, 880f};
        for (int b = 0; b < 4; b++) {
            for (int i = 0; i < beepLen; i++) {
                float t   = (float) i / SAMPLE_RATE;
                float env = (float)(Math.sin(Math.PI * i / beepLen));
                float wave = (float) Math.sin(2 * Math.PI * freqs[b] * t);
                int idx = b * beepLen + i;
                buf[idx] = (byte)(wave * env * 90 * sfxVolume);
            }
        }
        return buf;
    }

    private static byte[] genRoundEnd() {
        // Triumphant chord
        int len = (int)(SAMPLE_RATE * 1.0f);
        byte[] buf = new byte[len];
        float[] chord = {261.6f, 329.6f, 392f, 523.2f};
        for (int i = 0; i < len; i++) {
            float t   = (float) i / SAMPLE_RATE;
            float env = (float)(Math.min(1f, t * 8) * Math.exp(-t * 1.5f));
            float wave = 0;
            for (float f : chord) wave += (float) Math.sin(2 * Math.PI * f * t) / chord.length;
            buf[i] = (byte)(wave * env * 100 * sfxVolume);
        }
        return buf;
    }

    private static byte[] genDash() {
        int len = (int)(SAMPLE_RATE * 0.1f);
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            float t   = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * 40);
            float freq = 1200 - 800 * t * 10;
            float wave = (float) Math.sin(2 * Math.PI * Math.max(50, freq) * t);
            float noise = (float)(Math.random() * 2 - 1) * 0.3f;
            buf[i] = (byte)((wave + noise) * env * 80 * sfxVolume);
        }
        return buf;
    }

    private static byte[] genJump() {
        int len = (int)(SAMPLE_RATE * 0.15f);
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            float t   = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * 15);
            float freq = 300 + 500 * t / 0.15f;
            float wave = (float) Math.sin(2 * Math.PI * freq * t);
            buf[i] = (byte)(wave * env * 70 * sfxVolume);
        }
        return buf;
    }

    private static byte[] genBeep(float freq, float duration) {
        int len = (int)(SAMPLE_RATE * duration);
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            float t   = (float) i / SAMPLE_RATE;
            float env = (float)(Math.sin(Math.PI * i / len));
            buf[i] = (byte)((float) Math.sin(2 * Math.PI * freq * t) * env * 80 * sfxVolume);
        }
        return buf;
    }

    private static byte[] genRankStamp() {
        // Impact bass + metallic hit
        int len = (int)(SAMPLE_RATE * 0.8f);
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            float t   = (float) i / SAMPLE_RATE;
            float env = (float) Math.exp(-t * 8);
            float bass = (float) Math.sin(2 * Math.PI * 40 * t) * 0.6f;
            float metal = (float)(Math.random() * 2 - 1) * (float) Math.exp(-t * 20) * 0.4f;
            buf[i] = (byte)((bass + metal) * env * 110 * sfxVolume);
        }
        return buf;
    }

    // ================================================================
    //  Play helper — phát PCM buffer kèm BGM ducking (cân bằng âm thanh)
    // ================================================================
    private static void setBGMVolume(int volume) {
        try {
            if (synthesizer != null && synthesizer.isOpen()) {
                MidiChannel[] channels = synthesizer.getChannels();
                for (MidiChannel ch : channels) {
                    if (ch != null) {
                        ch.controlChange(7, volume); // 7 is Volume Controller (0..127)
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static void playSFX(byte[] buf) {
        playSFX(buf, false);
    }

    private static void playSFX(byte[] buf, boolean duckBGM) {
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            DataLine.Info info = new DataLine.Info(Clip.class, fmt);
            if (!AudioSystem.isLineSupported(info)) return;

            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(fmt, buf, 0, buf.length);
            
            if (duckBGM) {
                // Tắt/Hạ nhỏ nhạc nền BGM xuống mức rất nhỏ (10) để âm thanh Skill bùng nổ lực nhất
                setBGMVolume(10);
            }
            
            clip.addLineListener(e -> {
                if (e.getType() == LineEvent.Type.STOP) {
                    if (duckBGM) {
                        // Khôi phục nhạc nền BGM (100) sau khi tung skill xong
                        setBGMVolume(100);
                    }
                    clip.close();
                }
            });
            FloatControl vol = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            // Convert 0..1 → dB (-40..0). Nếu là Skill/Ultimate (duckBGM) thì cho sfx max volume (1.0f)
            float effectiveVol = duckBGM ? 1.0f : sfxVolume;
            float db = Math.max(-40f, (float)(20.0 * Math.log10(Math.max(0.0001, effectiveVol))));
            if (vol.getMinimum() <= db && db <= vol.getMaximum()) vol.setValue(db);
            clip.start();
        } catch (Exception e) {
            // Silently ignore nếu sound card không hỗ trợ
        }
    }

    // ================================================================
    //  MIDI sequence builders
    // ================================================================

    /** Battle theme: heavy & fast */
    private static Sequence buildBattleSequence() throws InvalidMidiDataException {
        Sequence seq = new Sequence(Sequence.PPQ, 16);
        Track t = seq.createTrack();

        // Program: Channel 0 = Lead Synth (81), Ch1 = Bass (38), Ch9 = Drums
        addProg(t, 0,  0,  81);  // Lead: Synth Lead
        addProg(t, 0,  1,  38);  // Bass: Synth Bass
        addProg(t, 0,  2,  48);  // Strings

        // === BASS LINE (channel 1) — 2 bar pattern ===
        int[] bassNotes = {36, 36, 38, 36, 39, 36, 41, 43};
        int[] bassLen   = { 8,  4,  4,  8,  4,  4,  8,  8};
        int pos = 0;
        for (int rep = 0; rep < 4; rep++) {
            int tp = rep * 128;
            for (int i = 0; i < bassNotes.length; i++) {
                addNote(t, tp + pos % 128, 1, bassNotes[i], 90, bassLen[i]);
                pos += bassLen[i];
                if (pos >= 128) pos -= 128;
            }
        }

        // === MELODY LINE (channel 0) — energetic ===
        int[] mel  = {72, 74, 76, 72, 79, 76, 74, 72, 71, 69, 72, 74};
        int[] mLen = { 8,  8,  8, 16,  8,  8,  8,  8,  8,  8, 16, 24};
        pos = 0;
        for (int rep = 0; rep < 2; rep++) {
            int tp = rep * 128;
            for (int i = 0; i < mel.length; i++) {
                addNote(t, tp + pos % 128, 0, mel[i], 100, mLen[i]);
                pos += mLen[i];
                if (pos >= 128) pos = 0;
            }
        }

        // === CHORDS (channel 2) ===
        int[][] chords = {{60,64,67}, {57,60,64}, {58,62,65}, {59,62,65}};
        for (int rep = 0; rep < 4; rep++) {
            int[] chord = chords[rep % chords.length];
            for (int note : chord) addNote(t, rep * 64, 2, note, 65, 62);
        }

        // === DRUMS (channel 9) ===
        // Kick = 36, Snare = 38, HiHat = 42, OpenHat = 46
        for (int bar = 0; bar < 4; bar++) {
            int bp = bar * 64;
            // Kick
            addNote(t, bp + 0,  9, 36, 110, 6);
            addNote(t, bp + 24, 9, 36, 100, 6);
            addNote(t, bp + 40, 9, 36,  90, 6);
            // Snare
            addNote(t, bp + 16, 9, 38, 105, 6);
            addNote(t, bp + 48, 9, 38, 100, 6);
            // HiHat 8th
            for (int h = 0; h < 8; h++) addNote(t, bp + h * 8, 9, 42, 70, 4);
            // Open hat
            addNote(t, bp + 32, 9, 46, 85, 8);
        }

        // End of sequence (256 ticks = 4 bars)
        t.add(createMeta(47, 256));
        return seq;
    }

    /** Title theme: atmospheric & epic */
    private static Sequence buildTitleSequence() throws InvalidMidiDataException {
        Sequence seq = new Sequence(Sequence.PPQ, 16);
        Track t = seq.createTrack();

        addProg(t, 0, 0, 95);  // Ch0: Pad (Atmosphere)
        addProg(t, 0, 1, 50);  // Ch1: String Ensemble
        addProg(t, 0, 2, 27);  // Ch2: Electric Guitar (clean)

        // Slow chord progression (Am - F - C - G)
        int[][] progression = {
            {57,60,64},  // Am
            {53,57,60},  // F
            {48,52,55},  // C
            {55,59,62}   // G
        };
        for (int rep = 0; rep < 4; rep++) {
            for (int r = 0; r < 4; r++) {
                int tp = rep * 256 + r * 64;
                for (int note : progression[r]) {
                    addNote(t, tp, 0, note, 70, 62);    // Pad
                    addNote(t, tp, 1, note - 12, 60, 62); // Bass string
                }
            }
        }

        // Arpeggio melody line
        int[] arp  = {69, 72, 76, 81, 76, 72, 69, 67};
        int[] aLen = { 8, 8,  8,  16, 8,  8,  8,  16};
        pos_ref[0] = 0;
        for (int rep = 0; rep < 8; rep++) {
            for (int i = 0; i < arp.length; i++) {
                addNote(t, pos_ref[0], 2, arp[i], 75, aLen[i] - 2);
                pos_ref[0] += aLen[i];
            }
        }

        t.add(createMeta(47, 1024));
        return seq;
    }

    // Workaround: Java không cho dùng lambda với local pos var
    private static final int[] pos_ref = {0};

    // ─── MIDI helpers ─────────────────────────────────────────────

    private static void addProg(Track t, long tick, int ch, int prog) throws InvalidMidiDataException {
        ShortMessage sm = new ShortMessage();
        sm.setMessage(ShortMessage.PROGRAM_CHANGE, ch, prog, 0);
        t.add(new MidiEvent(sm, tick));
    }

    private static void addNote(Track t, long tick, int ch, int note, int vel, int dur) throws InvalidMidiDataException {
        ShortMessage on = new ShortMessage();
        on.setMessage(ShortMessage.NOTE_ON,  ch, note, vel);
        t.add(new MidiEvent(on, tick));

        ShortMessage off = new ShortMessage();
        off.setMessage(ShortMessage.NOTE_OFF, ch, note, 0);
        t.add(new MidiEvent(off, tick + dur));
    }

    private static MidiEvent createMeta(int type, long tick) throws InvalidMidiDataException {
        MetaMessage meta = new MetaMessage();
        meta.setMessage(type, new byte[0], 0);
        return new MidiEvent(meta, tick);
    }
}
