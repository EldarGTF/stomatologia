package com.stomatologia.client.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Короткий двухтоновый сигнал уведомления. Звук синтезируется, файл и модуль javafx-media не нужны;
 * без звуковой карты сигнал просто не играет.
 */
public final class Chime {

    private static final Logger log = LogManager.getLogger(Chime.class);

    static final float RATE = 44_100f;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
    private static final byte[] SOUND = tone();
    private static final AtomicBoolean PLAYING = new AtomicBoolean();

    private Chime() {
    }

    public static void play() {
        if (!PLAYING.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
                line.open(FORMAT);
                line.start();
                line.write(SOUND, 0, SOUND.length);
                line.drain();
            } catch (Exception | LinkageError e) {
                log.debug("Сигнал уведомления не воспроизведён: {}", e.getMessage());
            } finally {
                PLAYING.set(false);
            }
        }, "notification-chime");
        t.setDaemon(true);
        t.start();
    }

    /** 16 бит, моно, little-endian: две ноты с затуханием. */
    static byte[] tone() {
        double[][] notes = {{880, 0.13}, {1175, 0.24}};
        int total = 0;
        for (double[] n : notes) {
            total += (int) (n[1] * RATE);
        }
        byte[] data = new byte[total * 2];
        int i = 0;
        for (double[] n : notes) {
            int samples = (int) (n[1] * RATE);
            for (int s = 0; s < samples; s++) {
                double t = s / RATE;
                double attack = Math.min(1, s / (RATE * 0.005));
                double envelope = attack * Math.exp(-t * 9);
                short v = (short) (Math.sin(2 * Math.PI * n[0] * t) * envelope * 0.35 * Short.MAX_VALUE);
                data[i++] = (byte) v;
                data[i++] = (byte) (v >> 8);
            }
        }
        return data;
    }
}
