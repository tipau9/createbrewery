package com.createbrewery.drunk;

/**
 * Follows a song as it is heard (see {@link MusicPulse#update}): counts the kicks, learns the
 * tempo, and hears the shape of a rave track - the build-up and the drop.
 *
 * <ul>
 *   <li>Build-up, two kinds: the kick has been gone for more than a bar while the music goes on
 *       (a breakdown), or the kick rolls, coming faster than the beat. Risers - louder than a few
 *       seconds ago, the hats busier - speed it up. A breakdown may last up to a minute.</li>
 *   <li>Drop: the kick comes back after a build-up and a pause of at least half a bar, or right
 *       after a moment of near silence. Harder the longer the build-up, and hardest right after
 *       that silence.</li>
 *   <li>A single stray kick in the middle of a breakdown is not the drop: if no second kick
 *       follows on the beat, the drop is taken back and the build-up goes on. So {@link #takeDrop}
 *       only answers once that second kick is in, a beat after {@link #drop} flashed up.</li>
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
    private double rollAt = Double.NEGATIVE_INFINITY;
    private double period = 0.5;
    private float calm, busy;
    private boolean kicking, dropped, lastFast;
    /** A drop not yet confirmed by a second kick (NaN when none), and what it replaced, to take it back. */
    private double pending = Double.NaN, keptLastDrop, keptLastKick;
    private float keptTension;

    /** Once a frame: what is heard now (kick, level and hats, each 0..1), the time in seconds. */
    void hear(float kick, float level, float hats, boolean heard, double now, float dt) {
        if (kick > 0.5f && !kicking) onKick(now);
        kicking = kick > 0.5f;
        // No second kick on the beat: that was a stray kick in the breakdown, not the drop.
        if (!Double.isNaN(pending) && now - pending > 2.5 * period) {
            lastDrop = keptLastDrop;
            lastKick = keptLastKick;
            tension = keptTension;
            drop = 0f;
            pending = Double.NaN;
        }

        double quiet = now - lastKick;
        float rising = Math.max(0f, level - calm) * 3f + Math.max(0f, hats - busy) * 2f;
        boolean gone = heard && quiet > Math.max(2.0, 4.0 * period) && quiet < 60.0;
        boolean rolling = heard && now - rollAt < period;
        if (gone || rolling) {
            tension += dt * (1f / 10f + rising);
        } else if (heard && quiet < 60.0) {
            tension -= dt / 4f; // a steady kick: the groove, slowly letting a build-up go
        } else {
            tension -= dt / 2f;
        }
        tension = Math.max(0f, Math.min(1f, tension));
        if (heard && level < 0.12f) hush = now;
        calm += (level - calm) * Math.min(1f, dt / 3f);
        busy += (hats - busy) * Math.min(1f, dt / 1f);
        drop *= (float) Math.exp(-dt * 1.2);
    }

    private void onKick(double now) {
        beats++;
        double gap = now - lastKick;
        // Once the tempo is known, a kick roll (or a stray kick) does not drag it away.
        boolean onBeat = beats < 16 ? gap > 0.25 && gap < 1.2 : gap > 0.75 * period && gap < 1.5 * period;
        if (onBeat) period += (gap - period) * 0.2;
        if (!Double.isNaN(pending) && gap < 2.5 * period) { // the kick is really back
            pending = Double.NaN;
            dropped = true;
        }
        // Two fast kicks in a row: a roll. One alone is a ghost kick or a broken beat.
        boolean fast = gap > 0.05 && gap < 0.6 * period;
        if (fast && lastFast) rollAt = now;
        lastFast = fast;

        boolean back = gap > Math.max(1.0, 2.0 * period) || now - hush < 1.0;
        if (back && tension > 0.3f && now - lastDrop > 15.0) {
            keptLastDrop = lastDrop;
            keptLastKick = lastKick;
            keptTension = tension;
            pending = now;
            drop = Math.min(1f, 0.4f + tension + (now - hush < 1.0 ? 0.3f : 0f));
            lastDrop = now;
            tension = 0f;
        }
        lastKick = now;
    }

    /** True once after each drop, a beat in (see above). */
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
