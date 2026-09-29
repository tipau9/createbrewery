package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Not a test: plays a recorded trace (Config.RECORD_MUSIC, logs/brewery-traces/*.csv) through the
 * drop detector and prints what it heard, for tuning against real songs.
 * {@code TRACE=path/to/song.csv ./gradlew test --tests '*DropReplay'}; output in the test report / stdout.
 */
class DropReplay {
    @Test
    @EnabledIfEnvironmentVariable(named = "TRACE", matches = ".+")
    void replay() throws Exception {
        List<String> lines = Files.readAllLines(Path.of(System.getenv("TRACE")));
        DropDetector d = new DropDetector();
        float dt = (float) KickDetector.SLICE;
        int beats = 0;
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split(",");
            double t = Double.parseDouble(f[0]);
            float kick = Float.parseFloat(f[5]), level = Float.parseFloat(f[6]), hats = Float.parseFloat(f[7]);
            float dropBefore = d.drop;
            d.hear(kick, level, hats, true, t, dt);
            if (d.drop > dropBefore + 0.1f) System.out.printf("%s  DROP %.2f (tension was high)%n", clock(t), d.drop);
            if (d.takeDrop()) System.out.printf("%s  drop confirmed%n", clock(t));
            if (Math.round(t / dt) % 50 == 0) {
                System.out.printf("%s  tension %.2f  kicks/s %d  bpm %.0f  level %.2f%n", clock(t), d.tension, d.beats - beats, 60 / d.period(), level);
                beats = d.beats;
            }
        }
    }

    private static String clock(double t) {
        return String.format("%d:%05.2f", (int) (t / 60), t % 60);
    }
}
