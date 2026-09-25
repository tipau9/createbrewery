package com.createbrewery.drunk;

/**
 * Follows a song as it is heard (see {@link MusicPulse#update}): counts the kicks, learns the
 * tempo, and hears the shape of a rave track - the build-up and the drop.
 *
 * <ul>
 *   <li>Build-up: the kick has been gone for more than a bar while the music goes on. It builds
 *       faster when the track rises towards something - louder than a few seconds ago, the
 *       hats and snares getting busier (risers, snare rolls).</li>
 *   <li>Drop: the kick comes back after a build-up. Harder the longer the build-up, and hardest
 *       right after the one moment of near silence many tracks take just before it.</li>
 * </ul>
 * A pause of a bar or two is not a build-up, a song without drums is not one long build-up, and
 * two drops are at least a quarter of a minute apart.
 */
final class DropDetector {
    int beats;
    /** 0..1 how far into a build-up. */
    float tension;
    /** 1 at a drop, dying away over a second or two. */
    float drop;
    private double lastKick = Double.NEGATIVE_INFINITY, lastDrop = Double.NEGATIVE_INFINITY, hush = Double.NEGATIVE_INFINITY;
    private double period = 0.5;
    private float calm, busy;
    private boolean kicking, dropped;

    /** Once a frame: what is heard now (kick, level and hats, each 0..1), the time in seconds. */
    void hear(float kick, float level, float hats, boolean heard, double now, float dt) {
        if (kick > 0.5f && !kicking) {
            beats++;
            double gap = now - lastKick;
            if (gap > 0.25 && gap < 1.2) period += (gap - period) * 0.2;
            if (tension > 0.3f && now - lastDrop > 15.0) {
                drop = Math.min(1f, 0.4f + tension + (now - hush < 1.0 ? 0.3f : 0f));
                dropped = true;
                lastDrop = now;
            }
            tension = 0f;
            lastKick = now;
        }
        kicking = kick > 0.5f;

        double quiet = now - lastKick;
        boolean building = heard && quiet > Math.max(2.0, 4.0 * period) && quiet < 30.0;
        if (building) {
            // Rising towards something: louder than lately, or the hats busier than lately.
            float rising = Math.max(0f, level - calm) * 3f + Math.max(0f, hats - busy) * 2f;
            tension = Math.min(1f, tension + dt * (1f / 10f + rising));
            if (level < 0.12f) hush = now;
        } else if (!heard || quiet >= 30.0) {
            tension = Math.max(0f, tension - dt / 2f);
        }
        calm += (level - calm) * Math.min(1f, dt / 3f);
        busy += (hats - busy) * Math.min(1f, dt / 1f);
        drop *= (float) Math.exp(-dt * 1.2);
    }

    /** True once after each drop. */
    boolean takeDrop() {
        boolean was = dropped;
        dropped = false;
        return was;
    }

    /** Seconds from the given time to the nearest beat. */
    double offBeat(double now) {
        double since = (now - lastKick) % period;
        return Math.min(since, period - since);
    }

    /** The beat's length in seconds, as learnt so far. */
    double period() {
        return period;
    }
}
