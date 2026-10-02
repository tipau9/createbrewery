package com.createbrewery.block.club;

/**
 * The amp rack's traffic light: the worst thing wrong with the rig, in plain words, and what to do
 * about it. Shown on the rack and in the DJ mixer.
 */
public final class AmpStatus {
    private AmpStatus() {}

    public enum Light { GREEN, YELLOW, RED }

    /** {@code zone} names the muted zone for "muted", -1 otherwise. */
    public record Status(Light light, String key, int zone) {
        public String langKey() {
            return "createbrewery.amp.status." + key;
        }
    }

    /**
     * @param counts  per zone how many speakers play it (see AmpRackBlockEntity.zoneCounts)
     * @param muted   per zone whether it is muted
     * @param limitDb the hardest any zone's limiters are working, in dB
     */
    public static Status of(boolean linked, boolean otherRack, boolean power, float limitDb, int[] counts, boolean[] muted) {
        if (!linked) return new Status(Light.RED, "unlinked", -1);
        if (otherRack) return new Status(Light.RED, "other_rack", -1);
        if (!power) return new Status(Light.RED, "power_off", -1);
        if (limitDb > 6f) return new Status(Light.RED, "too_loud", -1);
        if (limitDb >= 1f) return new Status(Light.YELLOW, "at_limit", -1);
        if (counts[AmpSettings.FLOOR] + counts[AmpSettings.SUBS] + counts[AmpSettings.DELAY] + counts[AmpSettings.ROOM] == 0) {
            return new Status(Light.YELLOW, "no_speakers", -1);
        }
        if (counts[AmpSettings.SUBS] == 0) return new Status(Light.YELLOW, "no_subs", -1);
        for (int z = 0; z < AmpSettings.ZONES; z++) {
            if (counts[z] > 0 && muted[z]) return new Status(Light.YELLOW, "muted", z);
        }
        return new Status(Light.GREEN, "ok", -1);
    }
}
