package com.createbrewery.drunk;

/**
 * Finds the eyes on a skin, for {@link DrugEyes}: which of the face's 8x8 pixels are eyes, and
 * whether they are drawn on the hat layer. Plain Java, pixels as ARGB of a 64x64 skin.
 *
 * <p>The skin colour is what the lower face mostly is, the hair what its top row mostly is; the
 * eyes are the row (and its neighbour) between them with the most pixels that are neither. A
 * face where that finds nothing gets the usual place, rows 3-4.
 */
public final class EyeFinder {
    private EyeFinder() {}

    /** Bit {@code y * 8 + x} of {@code eyes}: an eye pixel; of {@code hat}: drawn on the hat layer. */
    public record Eyes(long eyes, long hat) {
        public boolean at(int x, int y) {
            return (eyes >>> (y * 8 + x) & 1) != 0;
        }

        public boolean onHat(int x, int y) {
            return (hat >>> (y * 8 + x) & 1) != 0;
        }
    }

    public static final Eyes USUAL = new Eyes(0x7E7E000000L, 0L);

    public static Eyes find(int[] argb) {
        int[] face = new int[64];
        long hat = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int over = argb[(8 + y) * 64 + 40 + x];
                boolean onHat = (over >>> 24) > 128;
                face[y * 8 + x] = onHat ? over : argb[(8 + y) * 64 + 8 + x];
                if (onHat) hat |= 1L << (y * 8 + x);
            }
        }
        int skin = mode(face, 4, 8), hair = mode(face, 0, 1);
        int[] count = new int[8];
        long candidates = 0;
        for (int y = 2; y <= 5; y++) {
            for (int x = 0; x < 8; x++) {
                int c = face[y * 8 + x];
                if (distance(c, skin) > 60 && (hair == skin || distance(c, hair) > 40)) {
                    candidates |= 1L << (y * 8 + x);
                    count[y]++;
                }
            }
        }
        int best = -1;
        for (int y = 2; y <= 5; y++) if (count[y] >= 2 && (best < 0 || count[y] > count[best])) best = y;
        if (best < 0) return USUAL;
        long rows = 0xFFL << (best * 8);
        // The neighbouring row with the most eye pixels too, if it has a pair.
        int next = best > 2 && (best == 5 || count[best - 1] >= count[best + 1]) ? best - 1 : best + 1;
        if (next <= 5 && count[next] >= 2) rows |= 0xFFL << (next * 8);
        long eyes = candidates & rows;
        return new Eyes(eyes, hat & eyes);
    }

    /** The commonest opaque colour in rows {@code from} (incl.) to {@code to} (excl.). */
    private static int mode(int[] face, int from, int to) {
        java.util.Map<Integer, Integer> n = new java.util.HashMap<>();
        int best = 0, most = 0;
        for (int i = from * 8; i < to * 8; i++) {
            if ((face[i] >>> 24) < 128) continue;
            int c = n.merge(face[i] & 0xFFFFFF, 1, Integer::sum);
            if (c > most) {
                most = c;
                best = face[i] & 0xFFFFFF;
            }
        }
        return best;
    }

    private static int distance(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
    }
}
