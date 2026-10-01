package com.createbrewery.block.club;

import java.util.ArrayList;
import java.util.List;

/** The outlines of a moving head's gobos, as closed polygons in the plane of the spot. Free of Minecraft classes. */
final class GoboShape {
    private GoboShape() {}

    /**
     * Gobo 0 a circle, 1 a five-pointed star, 2 three dots, 3 a bar; anything else a circle. Each polygon is
     * {@code x0, y0, x1, y1, ...}, turned by {@code rotation} radians, within {@code radius} of the centre.
     */
    static List<float[]> shapes(int gobo, float rotation, float radius) {
        List<float[]> out = new ArrayList<>();
        switch (gobo) {
            case 1 -> {
                float[] p = new float[20];
                for (int i = 0; i < 10; i++) {
                    float r = i % 2 == 0 ? radius : radius * 0.45f;
                    double a = rotation + Math.PI * i / 5.0;
                    p[i * 2] = (float) (Math.cos(a) * r);
                    p[i * 2 + 1] = (float) (Math.sin(a) * r);
                }
                out.add(p);
            }
            case 2 -> {
                for (int d = 0; d < 3; d++) {
                    double a = rotation + Math.PI * 2 * d / 3.0;
                    float cx = (float) (Math.cos(a) * radius * 0.6), cy = (float) (Math.sin(a) * radius * 0.6);
                    float[] p = new float[16];
                    for (int i = 0; i < 8; i++) {
                        double b = Math.PI * 2 * i / 8.0;
                        p[i * 2] = cx + (float) (Math.cos(b) * radius * 0.3);
                        p[i * 2 + 1] = cy + (float) (Math.sin(b) * radius * 0.3);
                    }
                    out.add(p);
                }
            }
            case 3 -> {
                float hx = radius * 0.95f, hy = radius * 0.18f;
                float[] corners = {-hx, -hy, hx, -hy, hx, hy, -hx, hy};
                float c = (float) Math.cos(rotation), s = (float) Math.sin(rotation);
                float[] p = new float[8];
                for (int i = 0; i < 4; i++) {
                    p[i * 2] = corners[i * 2] * c - corners[i * 2 + 1] * s;
                    p[i * 2 + 1] = corners[i * 2] * s + corners[i * 2 + 1] * c;
                }
                out.add(p);
            }
            default -> {
                float[] p = new float[24];
                for (int i = 0; i < 12; i++) {
                    double a = rotation + Math.PI * 2 * i / 12.0;
                    p[i * 2] = (float) (Math.cos(a) * radius);
                    p[i * 2 + 1] = (float) (Math.sin(a) * radius);
                }
                out.add(p);
            }
        }
        return out;
    }
}
