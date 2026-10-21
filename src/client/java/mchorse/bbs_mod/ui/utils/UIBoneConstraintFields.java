package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Every field of a bone's limits, built the same for the rig and for the constraint track's
 * keyframes: the move and the scale locks first, a row each in the transform editor's order; then
 * per rotation axis a header naming it, with the lock and the limit switch, and the two angles
 * under it; then the stiffness of the three, shown only where an IK chain runs through the bone —
 * the one step that reads it.
 */
public class UIBoneConstraintFields
{
    public final UIIcon lockTranslateX;
    public final UIIcon lockTranslateY;
    public final UIIcon lockTranslateZ;
    public final UIIcon lockScaleX;
    public final UIIcon lockScaleY;
    public final UIIcon lockScaleZ;
    public final UIIcon lockX;
    public final UIIcon lockY;
    public final UIIcon lockZ;
    public final UIToggle limitX;
    public final UIToggle limitY;
    public final UIToggle limitZ;
    public final UISliderTrackpad minX;
    public final UISliderTrackpad minY;
    public final UISliderTrackpad minZ;
    public final UISliderTrackpad maxX;
    public final UISliderTrackpad maxY;
    public final UISliderTrackpad maxZ;
    public final UISliderTrackpad stiffnessX;
    public final UISliderTrackpad stiffnessY;
    public final UISliderTrackpad stiffnessZ;

    private final UIElement stiffnessTitle;
    private final UIElement stiffnessRow;

    private final Supplier<BoneConstraint> current;
    private final Consumer<Consumer<BoneConstraint>> edit;
    private final Runnable changed;

    /**
     * @param current the limits the fields show, read live by the lock icons
     * @param edit writes a change to the limits as one edit
     * @param changed called after a switch flips, for the host to refill and re-lay the fields
     */
    public UIBoneConstraintFields(Supplier<BoneConstraint> current, Consumer<Consumer<BoneConstraint>> edit, Runnable changed)
    {
        this.current = current;
        this.edit = edit;
        this.changed = changed;

        this.lockTranslateX = this.axisLock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_TRANSLATE, UIKeys.GENERAL_X, Colors.RED, (c) -> c.lockTranslateX, (c, v) -> c.lockTranslateX = v);
        this.lockTranslateY = this.axisLock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_TRANSLATE, UIKeys.GENERAL_Y, Colors.GREEN, (c) -> c.lockTranslateY, (c, v) -> c.lockTranslateY = v);
        this.lockTranslateZ = this.axisLock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_TRANSLATE, UIKeys.GENERAL_Z, Colors.BLUE, (c) -> c.lockTranslateZ, (c, v) -> c.lockTranslateZ = v);
        this.lockScaleX = this.axisLock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_SCALE, UIKeys.GENERAL_X, Colors.RED, (c) -> c.lockScaleX, (c, v) -> c.lockScaleX = v);
        this.lockScaleY = this.axisLock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_SCALE, UIKeys.GENERAL_Y, Colors.GREEN, (c) -> c.lockScaleY, (c, v) -> c.lockScaleY = v);
        this.lockScaleZ = this.axisLock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK_SCALE, UIKeys.GENERAL_Z, Colors.BLUE, (c) -> c.lockScaleZ, (c, v) -> c.lockScaleZ = v);

        this.lockX = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK.format(UIKeys.GENERAL_X), (c) -> c.lockX, (c, v) -> c.lockX = v);
        this.lockY = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK.format(UIKeys.GENERAL_Y), (c) -> c.lockY, (c, v) -> c.lockY = v);
        this.lockZ = this.lock(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LOCK.format(UIKeys.GENERAL_Z), (c) -> c.lockZ, (c, v) -> c.lockZ = v);

        this.limitX = this.limit(UIKeys.GENERAL_X, (c, v) -> c.limitX = v);
        this.limitY = this.limit(UIKeys.GENERAL_Y, (c, v) -> c.limitY = v);
        this.limitZ = this.limit(UIKeys.GENERAL_Z, (c, v) -> c.limitZ = v);

        this.minX = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.minX = v);
        this.minY = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.minY = v);
        this.minZ = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MIN, UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.minZ = v);
        this.maxX = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.maxX = v);
        this.maxY = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.maxY = v);
        this.maxZ = this.angle(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_MAX, UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.maxZ = v);

        this.stiffnessX = this.stiffness(UIKeys.GENERAL_X, Colors.RED, (c, v) -> c.stiffnessX = v);
        this.stiffnessY = this.stiffness(UIKeys.GENERAL_Y, Colors.GREEN, (c, v) -> c.stiffnessY = v);
        this.stiffnessZ = this.stiffness(UIKeys.GENERAL_Z, Colors.BLUE, (c, v) -> c.stiffnessZ = v);

        this.stiffnessTitle = UI.label(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_STIFFNESS_TITLE);
        this.stiffnessRow = UI.row(this.stiffnessX, this.stiffnessY, this.stiffnessZ);
    }

    /**
     * The per-axis lock as a padlock: open while the axis turns, closed when it stays where the keys
     * put it. The glyph is the state, read live from the limits; a locked axis also gets the
     * selection highlight behind it.
     */
    private UIIcon lock(IKey tooltip, Predicate<BoneConstraint> getter, BiConsumer<BoneConstraint, Boolean> setter)
    {
        UIIcon icon = new UIIcon(() -> getter.test(this.current.get()) ? Icons.LOCKED : Icons.UNLOCKED, (b) ->
        {
            boolean locked = getter.test(this.current.get());

            this.edit.accept((c) -> setter.accept(c, !locked));
            this.changed.run();
        })
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (getter.test(UIBoneConstraintFields.this.current.get()))
                {
                    context.batcher.highlight(this.area, Direction.BOTTOM);
                }

                super.renderSkin(context);
            }
        };

        icon.wh(UIConstants.ICON_SIZE, UIConstants.CONTROL_HEIGHT);
        icon.tooltip(tooltip);

        return icon;
    }

    /** A move or scale lock: as wide as a pad of the transform editor's row, in its axis' colour like the pad's number. */
    private UIIcon axisLock(IKey kind, IKey axis, int color, Predicate<BoneConstraint> getter, BiConsumer<BoneConstraint, Boolean> setter)
    {
        UIIcon icon = this.lock(kind.format(axis), getter, setter);

        icon.w(0);
        icon.iconColor(Colors.A100 | color);

        return icon;
    }

    private UIToggle limit(IKey axis, BiConsumer<BoneConstraint, Boolean> setter)
    {
        UIToggle toggle = new UIToggle(IKey.EMPTY, (b) ->
        {
            this.edit.accept((c) -> setter.accept(c, b.getValue()));
            this.changed.run();
        });

        /* As tall as the header row, so its switch lines up with the axis name and the lock. */
        toggle.w(UIConstants.SWITCH_WIDTH).h(UIConstants.CONTROL_HEIGHT);
        toggle.tooltip(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_LIMIT.format(axis));

        return toggle;
    }

    private UISliderTrackpad angle(IKey kind, IKey axis, int color, BiConsumer<BoneConstraint, Float> setter)
    {
        UISliderTrackpad pad = new UISliderTrackpad((v) -> this.edit.accept((c) -> setter.accept(c, v.floatValue())));

        pad.angle180();
        pad.textbox.setColor(color);
        pad.tooltip(IKey.constant("%s (%s)").format(kind, axis));

        return pad;
    }

    private UISliderTrackpad stiffness(IKey axis, int color, BiConsumer<BoneConstraint, Float> setter)
    {
        UISliderTrackpad pad = new UISliderTrackpad((v) -> this.edit.accept((c) -> setter.accept(c, v.floatValue())));

        pad.normalized();
        pad.textbox.setColor(color);
        pad.tooltip(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_STIFFNESS.format(axis));

        return pad;
    }

    /** An axis' header: its name on the left, the lock and the limit switch pinned right. */
    private static UIElement header(IKey axis, UIIcon lock, UIToggle limit)
    {
        UIElement row = new UIElement();

        row.row(UIConstants.MARGIN).preferred(0).height(UIConstants.CONTROL_HEIGHT);
        row.add(UI.label(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_AXIS.format(axis), UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F), lock, limit);

        return row;
    }

    /** The rows top to bottom: the move and the scale locks, each rotation axis' header and angles, then the stiffness. */
    public UIElement[] rows()
    {
        return new UIElement[] {
            UI.row(UIConstants.MARGIN, 0, UIConstants.CONTROL_HEIGHT, UI.marker(Icons.ALL_DIRECTIONS, UIKeys.TRANSFORMS_TRANSLATE), this.lockTranslateX, this.lockTranslateY, this.lockTranslateZ),
            UI.row(UIConstants.MARGIN, 0, UIConstants.CONTROL_HEIGHT, UI.marker(Icons.SCALE, UIKeys.TRANSFORMS_SCALE), this.lockScaleX, this.lockScaleY, this.lockScaleZ),
            header(UIKeys.GENERAL_X, this.lockX, this.limitX),
            UI.row(this.minX, this.maxX),
            header(UIKeys.GENERAL_Y, this.lockY, this.limitY),
            UI.row(this.minY, this.maxY),
            header(UIKeys.GENERAL_Z, this.lockZ, this.limitZ),
            UI.row(this.minZ, this.maxZ),
            this.stiffnessTitle,
            this.stiffnessRow
        };
    }

    /** Every field, for a host that greys them out together. */
    public List<UIElement> all()
    {
        return List.of(this.lockTranslateX, this.lockTranslateY, this.lockTranslateZ, this.lockScaleX, this.lockScaleY, this.lockScaleZ,
            this.lockX, this.lockY, this.lockZ, this.limitX, this.limitY, this.limitZ,
            this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ,
            this.stiffnessX, this.stiffnessY, this.stiffnessZ);
    }

    /** Push the limits into the fields; the stiffness shows only with {@code stiffness}. */
    public void fill(BoneConstraint c, boolean stiffness)
    {
        this.limitX.setValue(c.limitX);
        this.limitY.setValue(c.limitY);
        this.limitZ.setValue(c.limitZ);
        this.minX.setValue(c.minX);
        this.minY.setValue(c.minY);
        this.minZ.setValue(c.minZ);
        this.maxX.setValue(c.maxX);
        this.maxY.setValue(c.maxY);
        this.maxZ.setValue(c.maxZ);
        this.stiffnessX.setValue(c.stiffnessX);
        this.stiffnessY.setValue(c.stiffnessY);
        this.stiffnessZ.setValue(c.stiffnessZ);
        this.stiffnessTitle.setVisible(stiffness);
        this.stiffnessRow.setVisible(stiffness);
    }

    /** Greys the fields with their host; an axis' angles stay grey while it isn't limited. */
    public void setEnabled(boolean enabled, BoneConstraint c)
    {
        for (UIElement element : this.all())
        {
            element.setEnabled(enabled);
        }

        this.minX.setEnabled(enabled && c.limitX);
        this.maxX.setEnabled(enabled && c.limitX);
        this.minY.setEnabled(enabled && c.limitY);
        this.maxY.setEnabled(enabled && c.limitY);
        this.minZ.setEnabled(enabled && c.limitZ);
        this.maxZ.setEnabled(enabled && c.limitZ);
    }
}
