package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropTrainerTest {
    @Test
    void readsDropsTxt() throws Exception {
        Path file = Files.createTempFile("drops", ".txt");
        Files.writeString(file, """
            # my songs
            Pursuit of Happiness.mp3: 1:02, 2:35.5
            On Sight.mp3: -
            ? Levels.mp3: 67
            """);
        Map<String, DropTrainer.Label> labels = DropTrainer.readLabels(file);
        assertEquals(List.of(62.0, 155.5), labels.get("Pursuit of Happiness.mp3").drops());
        assertTrue(labels.get("On Sight.mp3").checked() && labels.get("On Sight.mp3").drops().isEmpty());
        assertFalse(labels.get("Levels.mp3").checked(), "suggestion, not checked");
        assertEquals(67.0, labels.get("Levels.mp3").drops().get(0));
    }

    @Test
    void matchesDetectedAgainstMarkedDrops() {
        // Marked at 60 and 120; found a beat late at 60.5, a false one at 90, none near 120.
        DropTrainer.Score s = DropTrainer.match(List.of(60.0, 120.0), List.of(60.5, 90.0));
        assertEquals(new DropTrainer.Score(1, 1, 1), s);
        assertEquals(0.5, s.f1(), 1e-9);
        assertEquals(1.0, DropTrainer.match(List.of(), List.of()).f1() + 1.0, 1e-9, "no drops marked, none found: nothing to score");
    }

    @Test
    void theTrainedLineIsValidJavaForTheDefaults() {
        String line = DropTrainer.code(DropDetector.DEFAULT);
        assertTrue(line.startsWith("static final Params DEFAULT = new Params("));
        assertTrue(line.endsWith(");"));
    }
}
