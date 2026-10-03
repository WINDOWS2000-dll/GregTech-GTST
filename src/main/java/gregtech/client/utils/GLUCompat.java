package gregtech.client.utils;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class GLUCompat {

    private GLUCompat() {}

    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        float fH = (float) (Math.tan(Math.toRadians(fovy) / 2) * zNear);
        float fW = fH * aspect;
        GL11.glFrustum(-fW, fW, -fH, fH, zNear, zFar);
    }

    public static void gluLookAt(float eyeX, float eyeY, float eyeZ, float centerX, float centerY, float centerZ,
                                 float upX, float upY, float upZ) {
        float[] f = normalize(centerX - eyeX, centerY - eyeY, centerZ - eyeZ);
        float[] s = normalize(cross(f, new float[] { upX, upY, upZ }));
        float[] u = cross(s, f);

        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        matrix.put(s[0]).put(u[0]).put(-f[0]).put(0f);
        matrix.put(s[1]).put(u[1]).put(-f[1]).put(0f);
        matrix.put(s[2]).put(u[2]).put(-f[2]).put(0f);
        matrix.put(0f).put(0f).put(0f).put(1f);
        matrix.flip();

        GL11.glMultMatrixf(matrix);
        GL11.glTranslatef(-eyeX, -eyeY, -eyeZ);
    }

    public static boolean gluProject(float objx, float objy, float objz, FloatBuffer model, FloatBuffer proj,
                                     IntBuffer viewport, FloatBuffer win) {
        float[] m = toArray(model, 16);
        float[] p = toArray(proj, 16);

        float[] eye = multiply(m, new float[] { objx, objy, objz, 1f });
        float[] clip = multiply(p, eye);

        if (clip[3] == 0f) return false;

        float ndcX = clip[0] / clip[3];
        float ndcY = clip[1] / clip[3];
        float ndcZ = clip[2] / clip[3];

        int vx = viewport.get(viewport.position());
        int vy = viewport.get(viewport.position() + 1);
        int vw = viewport.get(viewport.position() + 2);
        int vh = viewport.get(viewport.position() + 3);

        win.put(vx + (1 + ndcX) * vw / 2f);
        win.put(vy + (1 + ndcY) * vh / 2f);
        win.put((1 + ndcZ) / 2f);
        return true;
    }

    public static boolean gluUnProject(float winx, float winy, float winz, FloatBuffer model, FloatBuffer proj,
                                       IntBuffer viewport, FloatBuffer obj) {
        float[] m = toArray(model, 16);
        float[] p = toArray(proj, 16);
        float[] a = multiplyMatrices(p, m);
        float[] inv = invert(a);
        if (inv == null) return false;

        int vx = viewport.get(viewport.position());
        int vy = viewport.get(viewport.position() + 1);
        int vw = viewport.get(viewport.position() + 2);
        int vh = viewport.get(viewport.position() + 3);

        float ndcX = (winx - vx) * 2f / vw - 1f;
        float ndcY = (winy - vy) * 2f / vh - 1f;
        float ndcZ = 2f * winz - 1f;

        float[] result = multiply(inv, new float[] { ndcX, ndcY, ndcZ, 1f });
        if (result[3] == 0f) return false;

        obj.put(result[0] / result[3]);
        obj.put(result[1] / result[3]);
        obj.put(result[2] / result[3]);
        return true;
    }

    private static float[] toArray(FloatBuffer buf, int size) {
        float[] arr = new float[size];
        int pos = buf.position();
        for (int i = 0; i < size; i++) {
            arr[i] = buf.get(pos + i);
        }
        return arr;
    }

    private static float[] multiplyMatrices(float[] m, float[] n) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += m[k * 4 + row] * n[col * 4 + k];
                }
                r[col * 4 + row] = sum;
            }
        }
        return r;
    }

    private static float[] multiply(float[] m, float[] v) {
        float[] r = new float[4];
        for (int row = 0; row < 4; row++) {
            float sum = 0f;
            for (int k = 0; k < 4; k++) {
                sum += m[k * 4 + row] * v[k];
            }
            r[row] = sum;
        }
        return r;
    }

    private static float[] normalize(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len == 0f) return new float[] { 0, 0, 0 };
        return new float[] { x / len, y / len, z / len };
    }

    private static float[] normalize(float[] v) {
        return normalize(v[0], v[1], v[2]);
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static float[] invert(float[] m) {
        float[] inv = new float[16];

        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] +
                m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] -
                m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] +
                m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] -
                m[12] * m[5] * m[10] + m[12] * m[6] * m[9];

        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (det == 0f) return null;

        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] -
                m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] +
                m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] -
                m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] +
                m[12] * m[1] * m[10] - m[12] * m[2] * m[9];

        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] +
                m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] -
                m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] +
                m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] -
                m[12] * m[1] * m[6] + m[12] * m[2] * m[5];

        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] -
                m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] +
                m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] -
                m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] +
                m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

        float invDet = 1f / det;
        for (int i = 0; i < 16; i++) {
            inv[i] *= invDet;
        }
        return inv;
    }
}
