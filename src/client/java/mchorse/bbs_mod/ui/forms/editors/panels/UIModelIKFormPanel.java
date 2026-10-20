package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ik.BoneIKIO;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.bones.BoneRole;
import mchorse.bbs_mod.ui.utils.bones.BoneRoles;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelIKManager;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mchorse.bbs_mod.ui.utils.UIIKControlFields;

public class UIModelIKFormPanel extends UIBoneListFormPanel
{
    public UIToggle debug;
    public UIToggle enabled;
    public UIToggle boneController;
    public UIBonePicker target;
    public UITrackpad chainLength;
    public UILabel chainPreview;
    public UIToggle pole;
    public UIBonePicker poleTarget;
    public UISliderTrackpad poleAngle;
    public UISliderTrackpad softness;
    public UISliderTrackpad weight;
    public UIToggle tipRotation;
    public UIToggle classic;
    public UIToggle stretch;
    public UIToggle squash;

    /* The chain's own parameters, hidden until the chain is switched on: with IK
     * off they describe nothing, and an empty panel says "tick this" far better
     * than a wall of dimmed controls. A bone's limits (a knee's) are the bone's
     * own, on the limits tab — the chain usually lives on the foot. */
    private UIElement poleRow;
    private UIElement chainLengthRow;
    private UIElement weightRow;
    private UISection advancedSection;

    public UIModelIKFormPanel(UIForm editor)
    {
        super(editor);

        this.bonePresets(ModelIKManager.INSTANCE, "_CopyModelIK",
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_IK_CONTEXT_NAME,
            this::toPresetData, this::applyPresetData
        );

        this.debug = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_DEBUG, (b) -> BBSSettings.ikDebug.enabled.set(b.getValue()));
        this.debug.setValue(BBSSettings.ikDebug.enabled.get());
        this.debug.context(() -> new UIDebugOverlayContextMenu(BBSSettings.ikDebug));

        this.boneController = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_BONE_CONTROLLER,
            (b) -> this.editBone((bone) -> bone.boneController.set(b.getValue())));
        this.boneController.h(UIConstants.CONTROL_HEIGHT);
        this.boneController.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_BONE_CONTROLLER_TOOLTIP);
        this.resetBone(this.boneController, (bone) -> bone.boneController);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_ENABLED, (b) ->
        {
            if (b.getValue()) this.editControl(c -> c.enabled = true);
            else
            {
                FormBone bone = this.selectedFormBone();
                if (bone == null) return;
                FormBone cleared = new FormBone(bone.getId());
                cleared.fromData(bone.toData());
                BoneIKIO.clearChain(cleared);
                cleared.ik.getOriginalValue().enabled = false;
                bone.copy(cleared, IValueListener.FLAG_UNMERGEABLE);
            }
            this.updateFields();
        });
        this.enabled.h(UIConstants.CONTROL_HEIGHT);

        this.target = new UIBonePicker((bone) ->
        {
            if (this.form == null || this.selectedBone.isEmpty())
            {
                return;
            }

            /* The eyedropper bypasses the popup's graying, so the cycle gate sits
             * on the shared callback — a cyclic pick is refused outright. */
            if (this.isCyclic(bone))
            {
                return;
            }

            this.form.bones.getOrCreate(this.selectedBone).ikTarget.set(bone);
            this.updateFields();
        });

        /* A target the chain itself drives never compiles — gray it out in the
         * picker instead of letting the pick happen and flagging it after. */
        this.target.menu((picker) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            this.fillBoneMenu(picker, this.readBone((b) -> b.ikTarget.get(), ""), this::isCyclic);
        });
        this.target.viewport(this.viewportBonePicking());
        this.target.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_TARGET);
        this.resetBone(this.target, (bone) -> bone.ikTarget);

        this.chainLength = new UITrackpad((v) ->
        {
            this.editBone((bone) -> bone.ikChainLength.set(Math.max(0, (int) v.floatValue())));
            this.updateFields();
        });
        this.chainLength.limit(0).integer();
        this.chainLength.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_LENGTH);
        this.resetBone(this.chainLength, (bone) -> bone.ikChainLength);

        /* The live meaning of the chain length number: the bones the chain
         * actually spans, root to tip — so "0 = up to the root" stops being
         * folklore and the animator sees exactly what the solve will move. */
        this.chainPreview = UI.label(IKey.EMPTY, UIConstants.LIST_ITEM_HEIGHT, Colors.LIGHTER_GRAY);
        this.chainPreview.labelAnchor(0F, 0.5F);

        this.pole = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_POLE, (b) ->
        {
            this.editControl((c) -> c.pole = b.getValue());
            this.updateFields();
        });
        this.pole.h(UIConstants.CONTROL_HEIGHT);

        this.poleTarget = new UIBonePicker((bone) ->
        {
            if (this.form == null || this.selectedBone.isEmpty())
            {
                return;
            }

            this.form.bones.getOrCreate(this.selectedBone).ikPoleTarget.set(bone);
            this.updateFields();
        });

        /* A pole on a chain bone is not fatal (the compiler falls back to the
         * auto pole), so nothing is grayed out here. */
        this.poleTarget.menu((picker) ->
        {
            if (this.selectedBone.isEmpty())
            {
                return;
            }

            this.fillBoneMenu(picker, this.readBone((b) -> b.ikPoleTarget.get(), ""), null);
        });
        this.poleTarget.viewport(this.viewportBonePicking());
        this.poleTarget.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_TARGET);
        this.resetBone(this.poleTarget, (bone) -> bone.ikPoleTarget);

        var controls = new UIIKControlFields(this::editControl);
        this.poleAngle = controls.poleAngle;
        this.softness = controls.softness;
        this.weight = controls.weight;

        this.tipRotation = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_TIP_ROTATION, (b) -> this.editBone((bone) -> bone.ikTipRotation.set(b.getValue())));
        this.stretch = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_STRETCH, (b) -> this.editBone((bone) -> bone.ikStretch.set(b.getValue())));
        this.stretch.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_STRETCH_TOOLTIP);

        this.squash = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_SQUASH, (b) -> this.editBone((bone) -> bone.ikSquash.set(b.getValue())));
        this.squash.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_SQUASH_TOOLTIP);

        this.classic = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC, (b) ->
        {
            this.editBone((bone) -> bone.ikClassic.set(b.getValue()));
            this.updateFields();
        });
        this.classic.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC_TOOLTIP);

        this.resetBone(this.tipRotation, (bone) -> bone.ikTipRotation);
        this.resetBone(this.stretch, (bone) -> bone.ikStretch);
        this.resetBone(this.squash, (bone) -> bone.ikSquash);
        this.resetBone(this.classic, (bone) -> bone.ikClassic);

        UISection settings = this.section(UIKeys.FORMS_EDITORS_MODEL_IK_SETTINGS, "ik.chain", true);

        /* The base covers 90% of chain authoring: target, pole, chain span.
         * enabled+target and pole+poleTarget each pair into one labelRow — the
         * toggle names itself in the label slot, the bone picker pins to the
         * shared value column (same grid as the pose editor's lighting+colour
         * row). Everything the animator touches rarely lives in the collapsed
         * "Advanced" section below, so the panel reads in one glance. */
        this.poleRow = UI.labelRow(this.pole, this.poleTarget);
        this.chainLengthRow = UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_LENGTH, this.chainLength);
        this.weightRow = UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_WEIGHT, this.weight);

        settings.fields.add(
            UI.labelRow(this.enabled, this.target),
            this.poleRow,
            this.chainLengthRow,
            this.chainPreview,
            this.weightRow
        );

        this.advancedSection = this.section(UIKeys.FORMS_EDITORS_MODEL_IK_ADVANCED, "ik.advanced", false);

        this.advancedSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_POLE_ANGLE, this.poleAngle),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_IK_SOFTNESS, this.softness),
            this.tipRotation,
            this.stretch,
            this.squash,
            this.classic
        );

        this.options.add(
            this.debugRow(this.debug, BBSSettings.ikDebug),
            this.bonesSearch,
            settings,
            this.advancedSection,
            this.boneController
        );
    }

    @Override
    public void startEdit(ModelForm form)
    {
        this.debug.setValue(BBSSettings.ikDebug.enabled.get());

        super.startEdit(form);
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
        this.boneController.setEnabled(enabled);
        this.target.setEnabled(enabled);
        this.chainLength.setEnabled(enabled);
        this.pole.setEnabled(enabled);
        this.poleTarget.setEnabled(enabled);
        this.poleAngle.setEnabled(enabled);
        this.softness.setEnabled(enabled);
        this.weight.setEnabled(enabled);
        this.tipRotation.setEnabled(enabled);
        this.stretch.setEnabled(enabled);
        this.squash.setEnabled(enabled);
        this.classic.setEnabled(enabled);
    }

    private void fillBoneMenu(UIBonePickerContextMenu picker, String current, Predicate<String> disabled)
    {
        if (this.modelInstance == null || this.modelInstance.model == null)
        {
            return;
        }

        picker.bones(this.modelInstance.model, this.modelInstance.getDisabledBones()).none().disabled(disabled).set(current);
    }

    /* Value access: the panel holds no data of its own — every read and write
     * goes to the form's bone properties, and undo picks the writes up itself. */

    private IKControl currentControl()
    {
        FormBone bone = this.selectedFormBone();

        return bone == null ? IKControl.DEFAULT : bone.ik.get();
    }

    /** Edits the selected bone's IK scalars as one value change (one undo entry). */
    private void editControl(Consumer<IKControl> edit)
    {
        this.editBone((bone) ->
        {
            IKControl control = this.form.ik.getOriginalValue().get(bone.getId()).copy();

            edit.accept(control);
            BaseValue.edit(this.form.ik, value -> value.getOriginalValue().controls.put(bone.getId(), control));
        });
    }

    /**
     * The rig's IK, read off the bone list instead of one click per bone. Three slots, right
     * to left: the chain (living on its tip; the bones it drives run as a line along the
     * tree), the controller (a target, or the pole the bend aims at), the bone's limits the
     * solve holds it by. A switched-off chain goes gray everywhere — it drives nothing this tick.
     */
    @Override
    protected void describeBones(BoneRoles roles)
    {
        this.forEachFormBone((bone) ->
        {
            if (bone.boneController.get())
            {
                roles.mark(bone.getId(), 1, BoneRole.TARGET);
            }

            if (!bone.constraints.get().isFree())
            {
                roles.mark(bone.getId(), 2, BoneRole.LIMIT);
            }
        });

        roles.step(this.owners(), RigStep.IK, 0, 1);
    }

    @Override
    protected void updateFields()
    {
        if (this.target == null || this.enabled == null)
        {
            return;
        }

        FormBone formBone = this.selectedFormBone();
        IKControl control = this.currentControl();

        String targetLabel = formBone == null ? "" : formBone.ikTarget.get();
        boolean hasChain = formBone != null && formBone.hasChain();
        boolean active = formBone != null && formBone.ik.getOriginalValue().enabled;
        boolean poleOn = formBone != null && control.pole;
        boolean canEdit = !this.selectedBone.isEmpty() && this.bones.isEnabled() && active;
        int chainLength = formBone == null ? 0 : formBone.ikChainLength.get();

        /* Cycle validation, but the two cases differ. A TARGET the chain itself drives
         * closes a feedback loop and the chain does NOT compile — loud "(CYCLE!)".
         * A POLE on a chain bone is not fatal: the compiler quietly drops it and the
         * chain solves with the rest-side auto pole instead, so it gets a softer
         * "on chain → auto pole" hint, not the does-not-compile marker. */
        boolean cyclicTarget = this.isCyclic(targetLabel);
        boolean cyclicPole = formBone != null && this.isCyclic(formBone.ikPoleTarget.get());

        String chain = hasChain ? this.chainPreviewText(chainLength) : "";

        /* The pickers show the bare bone name (what they hold), not a
         * prefixed sentence — the row label and tooltip already say what
         * the picker means. */
        this.target.setLabel(IKey.constant(this.formatBone(targetLabel) + (cyclicTarget ? UIKeys.FORMS_EDITORS_MODEL_IK_CYCLE.get() : "")));
        this.chainLength.setValue(chainLength);
        this.chainPreview.label = chain.isEmpty() ? UIKeys.FORMS_EDITORS_MODEL_IK_CHAIN_EMPTY : IKey.constant(chain);
        this.pole.setValue(poleOn);
        this.poleTarget.setLabel(IKey.constant(this.formatBone(formBone == null ? "" : formBone.ikPoleTarget.get()) + (cyclicPole ? UIKeys.FORMS_EDITORS_MODEL_IK_POLE_CYCLE.get() : "")));
        this.poleAngle.setValue(control.poleAngle);
        this.softness.setValue(control.softness);
        this.weight.setValue(control.weight);
        this.tipRotation.setValue(formBone != null && formBone.ikTipRotation.get());
        this.stretch.setValue(formBone != null && formBone.ikStretch.get());
        this.squash.setValue(formBone != null && formBone.ikSquash.get());
        this.classic.setValue(formBone != null && formBone.ikClassic.get());

        /* The classic toggle is loud about its fallback: a classic chain that
         * is not exactly two bones, or shares a bone with another enabled
         * chain, solves on the core instead — the label says so right where
         * the box was ticked, no runtime surprise. */
        boolean classicFallsBack = formBone != null && formBone.ikClassic.get() && this.classicFallsBack(formBone);

        this.classic.label = classicFallsBack ? UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC_FALLBACK : UIKeys.FORMS_EDITORS_MODEL_IK_CLASSIC;
        this.enabled.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
        this.enabled.setValue(active);
        this.boneController.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
        this.boneController.setValue(formBone != null && formBone.boneController.get());

        /* Off means gone, not dimmed: only the switch is left standing. */
        this.target.setVisible(active);
        this.poleRow.setVisible(active);
        this.chainLengthRow.setVisible(active);
        this.chainPreview.setVisible(active);
        this.weightRow.setVisible(active);
        this.advancedSection.setVisible(active);
        this.options.resize();

        this.target.setEnabled(canEdit);
        this.chainLength.setEnabled(canEdit);
        this.pole.setEnabled(canEdit);
        this.poleTarget.setEnabled(canEdit && poleOn);
        this.poleAngle.setEnabled(canEdit && poleOn);
        this.softness.setEnabled(canEdit);
        this.weight.setEnabled(canEdit);
        this.tipRotation.setEnabled(canEdit);
        this.stretch.setEnabled(canEdit);
        this.squash.setEnabled(canEdit);
        this.classic.setEnabled(canEdit);
    }

    /**
     * The bones the selected bone's chain spans, root to tip, as a readable
     * arrow path — the live meaning of the chain length number. Empty when the
     * bone has no chain (no target) or the model is missing.
     */
    private String chainPreviewText(int chainLength)
    {
        IModel model = this.modelInstance == null ? null : this.modelInstance.model;

        if (model == null || this.selectedBone.isEmpty())
        {
            return "";
        }

        return String.join(" → ", ModelIKRuntime.chainBones(model, this.selectedBone, chainLength));
    }

    /**
     * Whether the selected bone's classic-marked chain would actually solve on
     * the core: wrong shape (not exactly two directed bones) or a bone shared
     * with another enabled chain (overlapping chains merge into one core tree).
     * Mirrors the applier's routing, computed statically from the form.
     */
    private boolean classicFallsBack(FormBone formBone)
    {
        IModel model = this.modelInstance == null ? null : this.modelInstance.model;

        if (model == null || this.form == null)
        {
            return false;
        }

        if (!ModelIKRuntime.isClassicShape(model, this.selectedBone, formBone.ikChainLength.get(), formBone.ikTipRotation.get()))
        {
            return true;
        }

        List<String> mine = ModelIKRuntime.chainBones(model, this.selectedBone, formBone.ikChainLength.get());

        for (BaseValue value : this.form.bones.getAll())
        {
            if (!(value instanceof FormBone other) || other.getId().equals(this.selectedBone))
            {
                continue;
            }

            if (!other.hasChain() || !other.ik.get().enabled)
            {
                continue;
            }

            for (String bone : ModelIKRuntime.chainBones(model, other.getId(), other.ikChainLength.get()))
            {
                if (mine.contains(bone))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private String formatBone(String bone)
    {
        return bone == null || bone.isEmpty() ? "-" : bone;
    }

    /** Whether pointing the selected bone's chain at {@code bone} would close a feedback loop. */
    private boolean isCyclic(String bone)
    {
        if (bone == null || bone.isEmpty() || this.modelInstance == null || this.modelInstance.model == null)
        {
            return false;
        }

        int chainLength = this.readBone((b) -> b.ikChainLength.get(), 0);

        return ModelIKRuntime.isCyclicTarget(this.modelInstance.model, this.selectedBone, chainLength, bone);
    }

    protected MapType toPresetData()
    {
        return this.form == null ? new MapType() : BoneIKIO.write(this.form.bones);
    }

    protected void applyPresetData(MapType map)
    {
        if (this.form == null)
        {
            return;
        }

        BoneIKIO.read(map, this.form.bones, true);

        this.updateFields();
    }

}
