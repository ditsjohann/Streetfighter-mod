package com.streetfighter.client.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps the 2D fight (arena units, y up from the floor) onto a straight line through the real world.
 * The line runs from the player to the mob; walls are placed where the terrain gets too steep, so
 * fighters get pinned in the corner by real cliffs, trees and houses.
 */
public final class Arena {
    public static final float UNITS_PER_BLOCK = 66f;
    private static final double SAMPLE = 0.25;
    private static final double MAX_HALF = 11.0;

    private final Level level;
    public final Vec3 origin;   // world point for arena x == centerX, at the reference floor height
    public final Vec3 axis;     // horizontal unit vector, +arena x
    public final Vec3 normal;   // horizontal unit vector perpendicular to the axis (camera side)
    public final float centerX;
    public final float minX, maxX;
    private final double refY;
    private final double[] profile; // floor height every SAMPLE blocks from -MAX_HALF..MAX_HALF
    private final Map<Long, Double> columnCache = new HashMap<>();

    private Arena(Level level, Vec3 origin, Vec3 axis, float centerX, double refY) {
        this.level = level;
        this.origin = origin;
        this.axis = axis;
        this.normal = new Vec3(-axis.z, 0, axis.x);
        this.centerX = centerX;
        this.refY = refY;
        int n = (int) Math.round(MAX_HALF * 2 / SAMPLE) + 1;
        profile = new double[n];
        for (int i = 0; i < n; i++) {
            double t = -MAX_HALF + i * SAMPLE;
            profile[i] = surface(origin.x + axis.x * t, origin.z + axis.z * t);
        }
        // walk outwards from the centre until the floor jumps (cliff/wall) or drops away (pit/water)
        int mid = n / 2;
        int left = mid, right = mid;
        while (left > 0 && passable(profile[left], profile[left - 1])) left--;
        while (right < n - 1 && passable(profile[right], profile[right + 1])) right++;
        this.minX = centerX + (float) ((-MAX_HALF + left * SAMPLE) * UNITS_PER_BLOCK);
        this.maxX = centerX + (float) ((-MAX_HALF + right * SAMPLE) * UNITS_PER_BLOCK);
    }

    private boolean passable(double from, double to) {
        return !Double.isNaN(to) && Math.abs(to - from) <= 1.05 && Math.abs(to - refY) <= 2.6;
    }

    /** Builds the arena on the line from the player to the mob. */
    public static Arena build(Level level, Vec3 playerFeet, Vec3 mobFeet, Vec3 fallbackLook, float centerX) {
        Vec3 d = new Vec3(mobFeet.x - playerFeet.x, 0, mobFeet.z - playerFeet.z);
        if (d.lengthSqr() < 0.04) d = new Vec3(fallbackLook.x, 0, fallbackLook.z);
        if (d.lengthSqr() < 1e-6) d = new Vec3(1, 0, 0);
        Vec3 axis = d.normalize();
        Vec3 mid = new Vec3((playerFeet.x + mobFeet.x) / 2, playerFeet.y, (playerFeet.z + mobFeet.z) / 2);
        return new Arena(level, mid, axis, centerX, playerFeet.y);
    }

    /** Signed distance (in blocks) of a world position along the arena axis, relative to the origin. */
    public double along(Vec3 world) {
        return (world.x - origin.x) * axis.x + (world.z - origin.z) * axis.z;
    }

    public float toArenaX(Vec3 world) {
        return centerX + (float) (along(world) * UNITS_PER_BLOCK);
    }

    /** World position of an arena point; floor follows the real terrain under a body of the given half width. */
    public Vec3 toWorld(float ax, float ay, double halfWidth) {
        double t = (ax - centerX) / UNITS_PER_BLOCK;
        double x = origin.x + axis.x * t, z = origin.z + axis.z * t;
        return new Vec3(x, floor(t, x, z, halfWidth) + ay / UNITS_PER_BLOCK, z);
    }

    /** World position ignoring terrain (for effects floating in the air). */
    public Vec3 toWorldFlat(float ax, float ay) {
        double t = (ax - centerX) / UNITS_PER_BLOCK;
        return new Vec3(origin.x + axis.x * t, floorAt(ax) + ay / UNITS_PER_BLOCK, origin.z + axis.z * t);
    }

    public double floorAt(float ax) {
        double t = (ax - centerX) / UNITS_PER_BLOCK;
        return smoothProfile(t);
    }

    private double floor(double t, double x, double z, double halfWidth) {
        double smooth = smoothProfile(t);
        // never sink into a block: check the real columns under the body's footprint
        double exact = smooth;
        double hw = Math.max(0.2, halfWidth - 0.05);
        double[][] offsets = {{0, 0}, {hw, 0}, {-hw, 0}, {0, hw}, {0, -hw}};
        for (double[] o : offsets) {
            double ox = x + axis.x * o[0] + normal.x * o[1];
            double oz = z + axis.z * o[0] + normal.z * o[1];
            double s = cachedSurface(ox, oz);
            if (!Double.isNaN(s) && s - smooth < 1.1) exact = Math.max(exact, s);
        }
        return exact;
    }

    private double smoothProfile(double t) {
        double f = (t + MAX_HALF) / SAMPLE;
        int i = (int) Math.floor(f);
        if (i < 0) return valid(profile[0]);
        if (i >= profile.length - 1) return valid(profile[profile.length - 1]);
        double a = valid(profile[i]), b = valid(profile[i + 1]);
        return a + (b - a) * (f - i);
    }

    private double valid(double v) {
        return Double.isNaN(v) ? refY : v;
    }

    private double cachedSurface(double x, double z) {
        long key = BlockPos.asLong((int) Math.floor(x), 0, (int) Math.floor(z));
        return columnCache.computeIfAbsent(key, k -> surface(x, z));
    }

    /** Top of the highest solid block near the reference height with two free blocks above it. */
    private double surface(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int top = (int) Math.floor(refY + 2.5), bottom = (int) Math.floor(refY - 4);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) return Double.NaN;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double h = y + shape.max(net.minecraft.core.Direction.Axis.Y);
            BlockPos above = pos.above();
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                    && level.getBlockState(above).getCollisionShape(level, above).max(net.minecraft.core.Direction.Axis.Y) > 0.5) {
                continue; // inside a wall; keep looking lower
            }
            return h;
        }
        return Double.NaN;
    }

    public double refY() {
        return refY;
    }
}
