package com.createbrewery.drunk;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import org.joml.Matrix4f;

/**
 * Distant Horizons draws the far landscape into its own depth buffer, so in the vanilla one it
 * looks like sky. This hands the shader DH's depth and projection, so far land counts as land.
 * Only touched when Distant Horizons is loaded (see {@link DrunkClient}), so it stays optional.
 */
final class DhDepth {
    private DhDepth() {}

    /** DH's projection (its own near and far planes), from the last frame it drew. */
    private static final Matrix4f projection = new Matrix4f();
    private static volatile boolean seen;

    static void init() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                toJoml(event.value.dhProjectionMatrix, projection);
                seen = true;
            }
        });
    }

    /** DH's depth texture, or -1 while it has none. */
    static int texture() {
        var proxy = DhApi.Delayed.renderProxy;
        if (proxy == null) return -1;
        DhApiResult<Integer> id = proxy.getDhDepthTextureId();
        return id.success && id.payload != null && id.payload > 0 ? id.payload : -1;
    }

    /** Screen to camera-relative world for DH's depth, or null before DH has drawn anything. */
    static Matrix4f invViewProj(Matrix4f modelView) {
        if (!seen || texture() < 0) return null;
        return new Matrix4f(projection).mul(modelView).invert();
    }

    /**
     * DH's matrix fields are named row-then-column. A perspective projection has -1 in row 3,
     * column 2; if it turns up the other way round, the fields are column-major after all.
     */
    private static void toJoml(DhApiMat4f m, Matrix4f out) {
        out.set(m.m00, m.m10, m.m20, m.m30, m.m01, m.m11, m.m21, m.m31,
            m.m02, m.m12, m.m22, m.m32, m.m03, m.m13, m.m23, m.m33);
        if (Math.abs(out.m23() + 1f) > 0.01f && Math.abs(out.m32() + 1f) < 0.01f) out.transpose();
    }
}
