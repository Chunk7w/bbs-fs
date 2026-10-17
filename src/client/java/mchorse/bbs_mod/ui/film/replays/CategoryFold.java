package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.film.replays.tracks.FormTrackGroup;
import mchorse.bbs_mod.film.replays.tracks.TrackTree;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a category's key does to a timeline. A category doesn't filter, it arranges what is being
 * worked on and folds every other owner away:
 * <ul>
 *   <li>The recording: its Position opens, its other headings fold.</li>
 *   <li>Form: the part in focus — the one last picked in the viewport, by its track or by a key, the
 *       form itself when none — opens at its Pose, or its Transform when it has no pose, its other
 *       headings fold.</li>
 *   <li>An addon's: the owners with its tracks open, their headings without its tracks fold.</li>
 * </ul>
 * Only the other owners' own rows fold: what is inside them stays as it was left, so unfolding one
 * brings back its headings the way they were. Pressed again, the recording's and Form's keys page
 * on through the headings in the order they stand; with Shift, back. Anything folded or unfolded by
 * hand in what the key arranged starts the paging over. The view scrolls as little as it takes to
 * show what was arranged. Bones folded inside a pose stay as they were, and a category with nothing
 * on the timeline (BBSFP's clip tab shows clips instead) leaves the folds be.
 */
public class CategoryFold
{
    private TrackCategory category;
    private Set<String> targets = new HashSet<>();
    private int step;

    /** The folds of what the last press arranged, right after it; still matching, it hasn't been touched since. */
    private final Map<String, Boolean> left = new HashMap<>();

    /**
     * @param back  Shift: page back
     * @param focus the path of the part being worked on, "" for the form itself
     */
    public void apply(UIKeyframeEditor editor, FoldState<String> folds, TrackCategory category, boolean back, String focus)
    {
        if (editor == null)
        {
            return;
        }

        Set<UIKeyframeSheet.Section> all = new LinkedHashSet<>();
        Set<UIKeyframeSheet.Section> with = new HashSet<>();

        for (UIKeyframeSheet sheet : editor.view.getSheets())
        {
            boolean ofCategory = UIReplaysEditor.categoryOf(sheet) == category;

            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                all.add(section);

                if (ofCategory)
                {
                    with.add(section);
                }
            }
        }

        boolean builtin = category == TrackCategory.REPLAY || category == TrackCategory.FORM;
        List<UIKeyframeSheet.Section> targets = targets(all, with, category, focus);

        if (targets.isEmpty())
        {
            return;
        }

        Set<String> targetIds = new HashSet<>();

        for (UIKeyframeSheet.Section owner : targets)
        {
            targetIds.add(owner.id());
        }

        boolean again = builtin && category == this.category && targetIds.equals(this.targets) && this.untouched(all, targets, folds);
        int step = again ? this.step + (back ? -1 : 1) : 0;

        for (UIKeyframeSheet.Section owner : targets)
        {
            String page = null;

            if (category == TrackCategory.REPLAY)
            {
                page = page(headings(all, with, owner), step, UIReplaysEditor.POSITION_SECTION, null);
            }
            else if (category == TrackCategory.FORM)
            {
                String path = owner.id().substring(TrackTree.FORM_OWNER.length());

                page = page(headings(all, with, owner), step, FormTrackGroup.POSE.sectionId(path), FormTrackGroup.TRANSFORM.sectionId(path));
            }

            folds.set(owner.id(), true);

            for (UIKeyframeSheet.Section section : all)
            {
                if (section.equals(owner) || !section.isIn(owner))
                {
                    continue;
                }

                if (builtin)
                {
                    folds.set(section.id(), section.id().equals(page));
                }
                else if (!with.contains(section))
                {
                    folds.set(section.id(), false);
                }
            }
        }

        for (UIKeyframeSheet.Section section : all)
        {
            if (section.owner() && !targetIds.contains(section.id()))
            {
                folds.set(section.id(), false);
            }
        }

        editor.view.getDopeSheet().setExpanded(folds);
        editor.view.getDopeSheet().revealSection(targets.get(0).id());

        this.category = category;
        this.targets = targetIds;
        this.step = step;
        this.left.clear();

        for (UIKeyframeSheet.Section section : arranged(all, targets))
        {
            this.left.put(section.id(), folds.isExpanded(section.id()));
        }
    }

    /** The owners a key arranges: the recording, the form part in focus, or every owner with an addon's tracks. */
    private static List<UIKeyframeSheet.Section> targets(Set<UIKeyframeSheet.Section> all, Set<UIKeyframeSheet.Section> with, TrackCategory category, String focus)
    {
        List<UIKeyframeSheet.Section> targets = new ArrayList<>();
        String focused = TrackTree.ownerId(focus == null ? "" : focus);
        UIKeyframeSheet.Section root = null;
        UIKeyframeSheet.Section firstForm = null;

        for (UIKeyframeSheet.Section section : all)
        {
            if (!section.owner())
            {
                continue;
            }

            if (category == TrackCategory.REPLAY)
            {
                if (section.id().equals(TrackTree.RECORD))
                {
                    targets.add(section);
                }
            }
            else if (category == TrackCategory.FORM)
            {
                if (!section.id().startsWith(TrackTree.FORM_OWNER))
                {
                    continue;
                }

                if (section.id().equals(focused))
                {
                    targets.add(section);
                }

                if (section.id().equals(TrackTree.ownerId("")))
                {
                    root = section;
                }

                if (firstForm == null)
                {
                    firstForm = section;
                }
            }
            else if (with.contains(section))
            {
                targets.add(section);
            }
        }

        /* A part in focus that is gone (removed, or another actor's) leaves the form itself in focus. */
        if (category == TrackCategory.FORM && targets.isEmpty())
        {
            UIKeyframeSheet.Section fallback = root != null ? root : firstForm;

            if (fallback != null)
            {
                targets.add(fallback);
            }
        }

        return targets;
    }

    /** An owner's own headings with the category's tracks, in the order they stand on the timeline. */
    private static List<String> headings(Set<UIKeyframeSheet.Section> all, Set<UIKeyframeSheet.Section> with, UIKeyframeSheet.Section owner)
    {
        List<String> headings = new ArrayList<>();

        for (UIKeyframeSheet.Section section : all)
        {
            if (owner.equals(section.parent()) && with.contains(section))
            {
                headings.add(section.id());
            }
        }

        return headings;
    }

    /** The heading the paging has reached: counted from the preferred one, or the fallback when there is none. */
    private static String page(List<String> headings, int step, String preferred, String fallback)
    {
        if (headings.isEmpty())
        {
            return null;
        }

        int start = headings.indexOf(preferred);

        if (start < 0 && fallback != null)
        {
            start = headings.indexOf(fallback);
        }

        return headings.get(Math.floorMod(Math.max(start, 0) + step, headings.size()));
    }

    /** The arranged owners and every heading inside them. */
    private static List<UIKeyframeSheet.Section> arranged(Set<UIKeyframeSheet.Section> all, List<UIKeyframeSheet.Section> targets)
    {
        List<UIKeyframeSheet.Section> arranged = new ArrayList<>();

        for (UIKeyframeSheet.Section section : all)
        {
            for (UIKeyframeSheet.Section owner : targets)
            {
                if (section.isIn(owner))
                {
                    arranged.add(section);
                    break;
                }
            }
        }

        return arranged;
    }

    private boolean untouched(Set<UIKeyframeSheet.Section> all, List<UIKeyframeSheet.Section> targets, FoldState<String> folds)
    {
        List<UIKeyframeSheet.Section> arranged = arranged(all, targets);

        if (arranged.size() != this.left.size())
        {
            return false;
        }

        for (UIKeyframeSheet.Section section : arranged)
        {
            Boolean expanded = this.left.get(section.id());

            if (expanded == null || expanded != folds.isExpanded(section.id()))
            {
                return false;
            }
        }

        return true;
    }
}
