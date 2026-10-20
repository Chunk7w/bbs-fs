package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The rig's steps of a model's frame, in the one order they run. The shake is part of the pose: it
 * layers into the channels before their snapshot, so every step after it and every reader of the
 * channels sees the bones where they are drawn. The others run over the channels and write only the
 * bones' evaluated orientation and offset, each starting from what the step before it left.
 *
 * <p>Every pass of a frame runs its own set of them (the sets below), always in this order. The model
 * renderer is the one place that runs them; what a pass adds on top (a world frame or none, IK in the
 * shadow's sample only for a spline) stays written at its call there.</p>
 */
public enum RigStep
{
    /** Bone shake, an additive layer of the pose: the channels phase. */
    SHAKE,
    /** IK chains. */
    IK,
    /** The addons' pose hook ({@code FormPoseEvents.MODEL_POSE}): after IK, before the built-in physics. */
    HOOK,
    /** Chain physics, the one step with a memory over time. */
    PHYSICS,
    /** Spline IK, solved in its root's parent frame after the physics; it may move the whole model. */
    SPLINE,
    /** Bone limits, the last word on a bone's rotation. */
    LIMITS;

    /* The sets are enum sets: they iterate in the frame's order whatever order they were written in. */

    /** The channels phase: what layers into the pose before its snapshot. */
    public static final Set<RigStep> CHANNELS = steps(SHAKE);

    /** The render: every step over the channels. */
    public static final Set<RigStep> RENDER = steps(IK, HOOK, PHYSICS, SPLINE, LIMITS);

    /**
     * The matrix readers (anchors, the gizmo, trackers, the shadow): no simulation and no limits.
     * The gizmo probes this pass with nudged channels, so it has to stay stateless.
     */
    public static final Set<RigStep> READERS = steps(IK, HOOK, SPLINE);

    /** The IK bake: the chains alone, it keys only what they solve. */
    public static final Set<RigStep> BAKE = steps(IK);

    private static Set<RigStep> steps(RigStep first, RigStep... rest)
    {
        return Collections.unmodifiableSet(EnumSet.of(first, rest));
    }

    /**
     * A transform gesture began. The physics snapshots its simulation, so a rejected gesture can
     * put back what the drag drove, and the IK solve dump (when compiled on) opens for the drag.
     */
    public static void gestureStarted()
    {
        ModelPhysicsRuntime.checkpoint();
        ModelIKRuntime.logGesture(true);
    }

    /** The gesture is over, accepted or rejected: the snapshot is forgotten. */
    public static void gestureEnded()
    {
        ModelIKRuntime.logGesture(false);
        ModelPhysicsRuntime.dropCheckpoint();
    }

    /** The gesture was rejected and the pose is back where it began: the simulation goes back too. */
    public static void gestureRejected()
    {
        ModelPhysicsRuntime.rewindToCheckpoint();
    }
}
