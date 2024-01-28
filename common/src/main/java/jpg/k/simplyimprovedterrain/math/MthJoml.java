/*
 * Copyright (c) 2015-2023 JOML
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 * (Or maybe this is fair use. I'm not sure.)
 */
package jpg.k.simplyimprovedterrain.math;

import net.minecraft.util.Mth;
import org.joml.Math;
import org.joml.Matrix3f;

public class MthJoml {

    /**
     * @see org.joml.Matrix3f#rotation(float angle, float x, float y, float z)
     */
    public static Matrix3f rotation(float angle, float x, float y, float z, Matrix3f destination) {
        return rotation(Mth.sin(angle), Mth.cos(angle), x, y, z, destination);
    }

    /**
     * @see org.joml.Matrix3f#rotation(float angle, float x, float y, float z)
     */
    public static Matrix3f rotation(float sin, float cos, float x, float y, float z, Matrix3f destination) {
        float oneMinusCos = 1.0f - cos;
        float xy = x * y, xz = x * z, yz = y * z;
        destination.m00 = cos + x * x * oneMinusCos;
        destination.m10 = xy * oneMinusCos - z * sin;
        destination.m20 = xz * oneMinusCos + y * sin;
        destination.m01 = xy * oneMinusCos + z * sin;
        destination.m11 = cos + y * y * oneMinusCos;
        destination.m21 = yz * oneMinusCos - x * sin;
        destination.m02 = xz * oneMinusCos - y * sin;
        destination.m12 = yz * oneMinusCos + x * sin;
        destination.m22 = cos + z * z * oneMinusCos;
        return destination;
    }

    /**
     * @see org.joml.Matrix3f#rotationY(float angle)
     */
    public static Matrix3f rotationY(float angle, Matrix3f destination) {
        return rotationY(Mth.sin(angle), Mth.cos(angle), destination);
    }

    /**
     * @see org.joml.Matrix3f#rotationY(float angle)
     */
    public static Matrix3f rotationY(float sin, float cos, Matrix3f destination) {
        destination.m00 = cos;
        destination.m01 = 0.0f;
        destination.m02 = -sin;
        destination.m10 = 0.0f;
        destination.m11 = 1.0f;
        destination.m12 = 0.0f;
        destination.m20 = sin;
        destination.m21 = 0.0f;
        destination.m22 = cos;
        return destination;
    }

    /**
     * @see org.joml.Matrix3f#rotate(float angle, float x, float y, float z)
     */
    public static Matrix3f rotate(float angle, float x, float y, float z, Matrix3f source, Matrix3f destination) {
        return rotate(Mth.sin(angle), Mth.cos(angle), x, y, z, source, destination);
    }

    /**
     * @see org.joml.Matrix3f#rotate(float angle, float x, float y, float z)
     */
    public static Matrix3f rotate(float sin, float cos, float x, float y, float z, Matrix3f source, Matrix3f destination) {
        float oneMinusCos = 1.0f - cos;

        // rotation matrix elements:
        // m30, m31, m32, m03, m13, m23 = 0
        float xx = x * x, xy = x * y, xz = x * z;
        float yy = y * y, yz = y * z;
        float zz = z * z;
        float rm00 = xx * oneMinusCos + cos;
        float rm01 = xy * oneMinusCos + z * sin;
        float rm02 = xz * oneMinusCos - y * sin;
        float rm10 = xy * oneMinusCos - z * sin;
        float rm11 = yy * oneMinusCos + cos;
        float rm12 = yz * oneMinusCos + x * sin;
        float rm20 = xz * oneMinusCos + y * sin;
        float rm21 = yz * oneMinusCos - x * sin;
        float rm22 = zz * oneMinusCos + cos;

        // add temporaries for dependent values
        float nm00 = source.m00 * rm00 + source.m10 * rm01 + source.m20 * rm02;
        float nm01 = source.m01 * rm00 + source.m11 * rm01 + source.m21 * rm02;
        float nm02 = source.m02 * rm00 + source.m12 * rm01 + source.m22 * rm02;
        float nm10 = source.m00 * rm10 + source.m10 * rm11 + source.m20 * rm12;
        float nm11 = source.m01 * rm10 + source.m11 * rm11 + source.m21 * rm12;
        float nm12 = source.m02 * rm10 + source.m12 * rm11 + source.m22 * rm12;

        // set non-dependent values directly
        destination.m20 = source.m00 * rm20 + source.m10 * rm21 + source.m20 * rm22;
        destination.m21 = source.m01 * rm20 + source.m11 * rm21 + source.m21 * rm22;
        destination.m22 = source.m02 * rm20 + source.m12 * rm21 + source.m22 * rm22;

        // set other values
        destination.m00 = nm00;
        destination.m01 = nm01;
        destination.m02 = nm02;
        destination.m10 = nm10;
        destination.m11 = nm11;
        destination.m12 = nm12;
        return destination;
    }

    /**
     * @see org.joml.Matrix3f#rotateLocal(float angle, float x, float y, float z)
     */
    public static Matrix3f rotateLocal(float angle, float x, float y, float z, Matrix3f source, Matrix3f destination) {
        return rotateLocal(Mth.sin(angle), Mth.cos(angle), x, y, z, source, destination);
    }

    /**
     * @see org.joml.Matrix3f#rotateLocal(float angle, float x, float y, float z)
     */
    public static Matrix3f rotateLocal(float sin, float cos, float x, float y, float z, Matrix3f source, Matrix3f destination) {
        float oneMinusCos = 1.0f - cos;
        float xx = x * x, xy = x * y, xz = x * z;
        float yy = y * y, yz = y * z;
        float zz = z * z;
        float lm00 = xx * oneMinusCos + cos;
        float lm01 = xy * oneMinusCos + z * sin;
        float lm02 = xz * oneMinusCos - y * sin;
        float lm10 = xy * oneMinusCos - z * sin;
        float lm11 = yy * oneMinusCos + cos;
        float lm12 = yz * oneMinusCos + x * sin;
        float lm20 = xz * oneMinusCos + y * sin;
        float lm21 = yz * oneMinusCos - x * sin;
        float lm22 = zz * oneMinusCos + cos;
        float nm00 = lm00 * source.m00 + lm10 * source.m01 + lm20 * source.m02;
        float nm01 = lm01 * source.m00 + lm11 * source.m01 + lm21 * source.m02;
        float nm02 = lm02 * source.m00 + lm12 * source.m01 + lm22 * source.m02;
        float nm10 = lm00 * source.m10 + lm10 * source.m11 + lm20 * source.m12;
        float nm11 = lm01 * source.m10 + lm11 * source.m11 + lm21 * source.m12;
        float nm12 = lm02 * source.m10 + lm12 * source.m11 + lm22 * source.m12;
        float nm20 = lm00 * source.m20 + lm10 * source.m21 + lm20 * source.m22;
        float nm21 = lm01 * source.m20 + lm11 * source.m21 + lm21 * source.m22;
        float nm22 = lm02 * source.m20 + lm12 * source.m21 + lm22 * source.m22;
        destination.m00 = nm00;
        destination.m01 = nm01;
        destination.m02 = nm02;
        destination.m10 = nm10;
        destination.m11 = nm11;
        destination.m12 = nm12;
        destination.m20 = nm20;
        destination.m21 = nm21;
        destination.m22 = nm22;
        return destination;
    }

}
