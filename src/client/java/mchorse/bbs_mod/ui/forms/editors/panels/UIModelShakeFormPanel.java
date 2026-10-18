package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.shake.ShakeControl;
import mchorse.bbs_mod.cubic.shake.ShakeControls;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIShakeControlFields;
import mchorse.bbs_mod.ui.utils.bones.BoneRoles;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.ModelShakeManager;

import java.util.function.Consumer;

/**
 * The shake tab: pick a bone, switch its shake on and set how it shakes. The numbers are the
 * form's own shake controls; the form's shake track keys the same numbers over a take, the way
 * the solver tracks key their chains.
 */
public class UIModelShakeFormPanel extends UIBoneListFormPanel
{
    public UIToggle enabled;
    public UIShakeControlFields fields;

    /* Hidden while the bone doesn't shake: off means gone, as on the solver tabs. */
    private UIElement settings;
    private UISection amplitude;

    /** Shown when IK or Spline IK owns the bone's rotation: the solve would take the shake out of it. */
    private UILabel driven;

    public UIModelShakeFormPanel(UIForm editor)
    {
        super(editor);

        this.bonePresets(ModelShakeManager.INSTANCE, "_CopyModelShake",
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_SHAKE_CONTEXT_NAME,
            this::toPresetData, this::applyPresetData
        );

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_SHAKE_ENABLED, (b) -> this.setShakeEnabled(b.getValue()));
        this.fields = new UIShakeControlFields(this::editControl);

        this.driven = UI.label(UIKeys.FORMS_EDITORS_MODEL_SHAKE_DRIVEN, UIConstants.CONTROL_HEIGHT, Colors.ORANGE);
        this.driven.labelAnchor(0, 0.5F);
        this.driven.tooltip(UIKeys.FORMS_EDITORS_MODEL_SHAKE_DRIVEN_TOOLTIP);

        this.settings = UI.column(UIConstants.MARGIN, this.driven);
        this.settings.add(this.fields.settings());

        UISection settings = this.section(UIKeys.FORMS_EDITORS_MODEL_SHAKE_SETTINGS, "shake.settings", true);

        settings.fields.add(this.enabled, this.settings);

        this.amplitude = this.section(UIKeys.FORMS_EDITORS_MODEL_SHAKE_AMPLITUDE, "shake.amplitude", true);
        this.amplitude.fields.add(this.fields.amplitudes());

        this.options.add(this.bonesSearch, settings, this.amplitude);
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.enabled.setEnabled(enabled);
    }

    /** On starts still: every amplitude is zero until one is set. Off means gone, numbers and all. */
    private void setShakeEnabled(boolean enabled)
    {
        if (this.form == null || this.selectedBone.isEmpty())
        {
            return;
        }

        String bone = this.selectedBone;

        BaseValue.edit(this.form, IValueListener.FLAG_UNMERGEABLE, (form) ->
        {
            form.bones.getOrCreate(bone).shake.set(enabled);

            if (!enabled)
            {
                form.shake.getOriginalValue().controls.remove(bone);
            }
        });

        this.updateFields();
    }

    /** Edits the selected bone's control in the form's shake controls as one value change. */
    private void editControl(Consumer<ShakeControl> edit)
    {
        this.editBone((bone) ->
        {
            ShakeControl control = this.currentControl().copy();

            edit.accept(control);
            BaseValue.edit(this.form.shake, (value) -> value.getOriginalValue().controls.put(bone.getId(), control));
        });
    }

    /** The selected bone's own numbers; a bone that has none yet shows the still defaults. */
    private ShakeControl currentControl()
    {
        ShakeControl control = this.form == null ? null : this.form.shake.getOriginalValue().controls.get(this.selectedBone);

        return control == null ? ShakeControl.DEFAULT : control;
    }

    private boolean isRotationDriven()
    {
        IModel model = this.modelInstance == null ? null : this.modelInstance.model;

        return model != null && ModelIKRuntime.isRotationConstrained(model, this.form, this.selectedBone);
    }

    @Override
    protected void updateFields()
    {
        if (this.amplitude == null)
        {
            return;
        }

        FormBone bone = this.selectedFormBone();
        boolean on = bone != null && bone.hasShake();

        this.enabled.setEnabled(this.bones.isEnabled() && !this.selectedBone.isEmpty());
        this.enabled.setValue(on);
        this.settings.setVisible(on);
        this.amplitude.setVisible(on);
        this.driven.setVisible(on && this.isRotationDriven());
        this.fields.fill(this.currentControl());

        this.options.resize();
    }

    /** A shaking bone simply reads at full strength — shake is the only thing this tab sets. */
    @Override
    protected void describeBones(BoneRoles roles)
    {
        this.forEachFormBone((bone) ->
        {
            if (bone.hasShake())
            {
                roles.touch(bone.getId());
            }
        });
    }

    /** A preset is every shaking bone's shake, keyed by the bone. */
    protected MapType toPresetData()
    {
        MapType data = new MapType();

        if (this.form == null)
        {
            return data;
        }

        MapType bones = new MapType();
        ShakeControls controls = this.form.shake.getOriginalValue();

        this.forEachFormBone((bone) ->
        {
            if (bone.hasShake())
            {
                ShakeControl control = controls.controls.get(bone.getId());

                bones.put(bone.getId(), (control == null ? ShakeControl.DEFAULT : control).toData());
            }
        });

        if (!bones.isEmpty())
        {
            data.put("bones", bones);
        }

        return data;
    }

    /** A preset is a complete state: a bone it doesn't mention stops shaking. */
    protected void applyPresetData(MapType data)
    {
        if (this.form == null)
        {
            return;
        }

        MapType bones = data.getMap("bones");

        BaseValue.edit(this.form, IValueListener.FLAG_UNMERGEABLE, (form) ->
        {
            for (BaseValue value : form.bones.getAll())
            {
                if (value instanceof FormBone bone && bone.hasShake())
                {
                    bone.shake.set(false);
                }
            }

            form.shake.getOriginalValue().controls.clear();

            for (String id : bones.keys())
            {
                if (id.isEmpty() || !bones.has(id, BaseType.TYPE_MAP))
                {
                    continue;
                }

                ShakeControl control = new ShakeControl();

                control.fromData(bones.getMap(id));
                form.bones.getOrCreate(id).shake.set(true);
                form.shake.getOriginalValue().controls.put(id, control);
            }
        });

        this.updateFields();
    }
}
