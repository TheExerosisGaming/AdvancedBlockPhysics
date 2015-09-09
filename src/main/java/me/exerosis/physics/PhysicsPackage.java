package me.exerosis.physics;

import com.badlogic.gdx.math.Quaternion;
import org.bukkit.util.EulerAngle;

public final class PhysicsPackage {
    static final float ARMOR_BLOCK_SIZE = 0.625F;
    static final float ARMOR_BLOCK_SIZE_HALF = ARMOR_BLOCK_SIZE / 2;

    public static float getArmorBlockSize() {
        return ARMOR_BLOCK_SIZE;
    }

    public static float getArmorBlockSizeH() {
        return ARMOR_BLOCK_SIZE_HALF;
    }

    public static EulerAngle quaternionToEuler(Quaternion quaternion) {
        float sqw = quaternion.w * quaternion.w;
        float sqx = quaternion.x * quaternion.x;
        float sqy = quaternion.y * quaternion.y;
        float sqz = quaternion.z * quaternion.z;
        float unit = sqx + sqy + sqz + sqw;
        float test = quaternion.x * quaternion.y + quaternion.z * quaternion.w;
        if (test > 0.499D * unit)
            return new EulerAngle(Math.PI / 2, 2 * Math.atan2(quaternion.x, quaternion.w), 0.0D);
        if (test < -0.499D * unit)
            return new EulerAngle(-Math.PI / 2, -2 * Math.atan2(quaternion.x, quaternion.w), 0.0D);
        return new EulerAngle(Math.atan2(2 * quaternion.y * quaternion.w - 2 * quaternion.x * quaternion.z, sqx - sqy - sqz + sqw), -Math.atan2(2 * quaternion.x * quaternion.w - 2 * quaternion.y * quaternion.z, -sqx + sqy - sqz + sqw), -Math.asin(2 * test / unit));
    }
}