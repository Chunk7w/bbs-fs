package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.utils.Axis;

/**
 * Which channels of a bone's transform the gizmo may not move, packed into one int: three bits
 * per operation, X lowest — translate, then rotate, then scale. Zero locks nothing. It only
 * restrains the gizmo and its hotkeys; typed values, keyframes and playback stay free, like
 * Blender's transform locks.
 */
public final class TransformLocks
{
    public static final int TRANSLATE = 0;
    public static final int ROTATE = 3;
    public static final int SCALE = 6;

    public static final int ALL_AXES = 0b111;

    private TransformLocks()
    {
    }

    /** The locked axes of one operation as XYZ bits. */
    public static int axes(int locks, int operation)
    {
        return locks >> operation & ALL_AXES;
    }

    public static boolean isLocked(int locks, int operation, Axis axis)
    {
        return axis != null && (axes(locks, operation) & 1 << axis.ordinal()) != 0;
    }

    /** Whether every axis of the operation is locked, i.e. the operation is off altogether. */
    public static boolean isLockedFully(int locks, int operation)
    {
        return axes(locks, operation) == ALL_AXES;
    }

    public static int toggle(int locks, int operation, Axis axis)
    {
        return locks ^ 1 << operation + axis.ordinal();
    }
}
