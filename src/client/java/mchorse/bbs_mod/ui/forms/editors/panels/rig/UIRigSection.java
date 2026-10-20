package mchorse.bbs_mod.ui.forms.editors.panels.rig;

import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.panels.UIRigFormPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * One step's part of the rig tab: a section whose header carries the step's icon and name, what its
 * setting spans ("start → end") and, for a step a bone may have or not, the switch that sets it up
 * on the bone or removes it. Switched off, the section is its gray header alone; switched on, it
 * folds like any section. The step keeps its data where it always has — the section only shows the
 * part of it the selected bone is in.
 */
public abstract class UIRigSection extends UISection
{
    protected final UIRigFormPanel panel;
    public final RigStep step;

    /** Sets the step up on the bone or removes it; null for a step every bone has (the limits). */
    public final UIToggle toggle;

    private boolean on;
    private String info = "";

    protected UIRigSection(UIRigFormPanel panel, RigStep step, boolean switchable)
    {
        super(step.title());

        this.panel = panel;
        this.step = step;
        this.toggle = switchable ? new UIToggle(IKey.EMPTY, (b) -> this.panel.switchStep(this, b.getValue())) : null;

        if (this.toggle != null)
        {
            this.toggle.w(UIConstants.SWITCH_WIDTH);
            this.title.removeFromParent();

            UIElement header = new UIElement();

            header.row(UIConstants.MARGIN).preferred(0).height(HEADER_HEIGHT);
            header.add(this.title, this.toggle);
            this.prepend(header);
        }
    }

    /** Refills the section for the bone, the switch included. */
    public void refresh(String bone)
    {
        this.info = "";
        this.on = this.fill(bone);

        if (this.toggle != null)
        {
            this.toggle.setValue(this.on);
        }

        this.fields.setVisible(this.hasBody());
    }

    /** Whether the step has something on the selected bone. */
    public boolean isOn()
    {
        return this.on;
    }

    /** What the setting spans, after the name in the header. */
    protected void setInfo(String info)
    {
        this.info = info == null ? "" : info;
    }

    /** Fills the fields for the bone; whether the step has something on it. */
    protected abstract boolean fill(String bone);

    /** Sets the step up on the bone ({@code on}) or removes what the section shows. */
    public abstract void switched(String bone, boolean on);

    /** Grays the fields out with the panel. */
    public void setFieldsEnabled(boolean enabled)
    {
        if (this.toggle != null)
        {
            this.toggle.setEnabled(enabled);
        }
    }

    /** A switchable section has nothing under its header while it's off. */
    protected boolean hasBody()
    {
        return this.on || this.toggle == null;
    }

    /** Whether the header reads at full strength. */
    protected boolean isLit()
    {
        return this.on;
    }

    @Override
    protected void renderHeader(UIContext context, UILabel title)
    {
        FontRenderer font = context.batcher.getFont();
        Area area = title.area;
        int color = this.isLit() ? Colors.WHITE : Colors.GRAY;
        int x = area.x;
        int right = area.ex();

        /* The strip one pixel taller than the label, centred the way every section header is. */
        float middle = area.y + (area.h + 1) / 2F;
        int textY = area.y + (area.h + 1 - font.getHeight()) / 2;

        context.batcher.icon(this.step.icon, color, x, middle, 0F, 0.5F);
        x += ICON_WIDTH;

        if (this.hasBody())
        {
            renderArrow(context, right - ARROW_SIZE / 2F, middle, this.isExpanded(), color);
            right -= ARROW_SIZE + 2;
        }

        String label = font.limitToWidth(title.label.get(), right - x);

        context.batcher.textShadow(label, x, textY, color);
        x += font.getWidth(label) + UIConstants.MARGIN * 2;

        if (!this.info.isEmpty() && x < right)
        {
            context.batcher.textShadow(font.limitToWidth(this.info, right - x), x, textY, Colors.GRAY);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        /* Nothing folds under a switched off header. */
        return this.hasBody() && super.subMouseClicked(context);
    }
}
