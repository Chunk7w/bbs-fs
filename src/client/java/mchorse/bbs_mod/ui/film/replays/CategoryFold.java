package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.film.replays.tracks.FormTrackGroup;
import mchorse.bbs_mod.film.replays.tracks.TrackTree;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;

import java.util.ArrayList;
import java.util.Collections;
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
 *
 * <p>The same layout is how a heading starts the first time a timeline shows it — a new replay, a
 * part that turns up, a form just picked: see {@link #settle}. There are no other first folds.
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

        List<UIKeyframeSheet> sheets = editor.view.getSheets();
        Set<UIKeyframeSheet.Section> all = sections(sheets);
        Set<UIKeyframeSheet.Section> with = sectionsOf(sheets, category);
        List<UIKeyframeSheet.Section> targets = targets(all, with, category, focus);

        if (targets.isEmpty())
        {
            return;
        }

        Set<String> targetIds = ids(targets);
        boolean again = builtin(category) && category == this.category && targetIds.equals(this.targets) && this.untouched(all, targets, folds);
        int step = again ? this.step + (back ? -1 : 1) : 0;

        layout(all, with, targets, category, step).forEach(folds::set);

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

    /**
     * Fold the headings a timeline shows for the first time the way the category's key would lay
     * them out, as if it had just been pressed — for those headings only, the rest stays as it was.
     * What the key leaves alone, the insides of the owners it folds, starts the way that owner's own
     * key would lay it out: the recording at its Position, a part at its Pose. With nothing of the
     * category on the timeline, every new owner starts folded.
     *
     * @param fresh the headings shown for the first time, see {@link TrackTree#groupSheets}
     * @param focus the path of the part being worked on, "" for the form itself
     */
    public static void settle(List<UIKeyframeSheet> sheets, FoldState<String> folds, TrackCategory category, String focus, Set<String> fresh)
    {
        if (fresh.isEmpty())
        {
            return;
        }

        Set<UIKeyframeSheet.Section> all = sections(sheets);
        Set<UIKeyframeSheet.Section> with = sectionsOf(sheets, category);
        Map<String, Boolean> laid = layout(all, with, targets(all, with, category, focus), category, 0);

        for (UIKeyframeSheet.Section section : all)
        {
            if (!fresh.contains(section.id()))
            {
                continue;
            }

            Boolean expanded = laid.get(section.id());

            if (expanded == null)
            {
                /* An addon's key leaves its own headings as they were: new, they are what it is there for */
                expanded = !builtin(category) && with.contains(section) || ownLayout(sheets, all, section);
            }

            folds.set(section.id(), expanded);
        }
    }

    /** How the key of the owner a heading sits in would lay it out: the recording's or Form's. */
    private static boolean ownLayout(List<UIKeyframeSheet> sheets, Set<UIKeyframeSheet.Section> all, UIKeyframeSheet.Section section)
    {
        UIKeyframeSheet.Section owner = section;

        while (owner != null && !owner.owner())
        {
            owner = owner.parent();
        }

        if (owner == null)
        {
            return true;
        }

        TrackCategory category = owner.id().equals(TrackTree.RECORD) ? TrackCategory.REPLAY : TrackCategory.FORM;

        return layout(all, sectionsOf(sheets, category), Collections.singletonList(owner), category, 0).getOrDefault(section.id(), true);
    }

    /**
     * What a key sets, heading by heading: the owners it arranges open at the heading it has paged
     * to, every other owner folded. What is inside the owners it folds isn't in it — that stays.
     */
    private static Map<String, Boolean> layout(Set<UIKeyframeSheet.Section> all, Set<UIKeyframeSheet.Section> with, List<UIKeyframeSheet.Section> targets, TrackCategory category, int step)
    {
        Map<String, Boolean> layout = new HashMap<>();
        Set<String> targetIds = ids(targets);

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

            layout.put(owner.id(), true);

            for (UIKeyframeSheet.Section section : all)
            {
                if (section.equals(owner) || !section.isIn(owner))
                {
                    continue;
                }

                if (builtin(category))
                {
                    layout.put(section.id(), section.id().equals(page));
                }
                else if (!with.contains(section))
                {
                    layout.put(section.id(), false);
                }
            }
        }

        for (UIKeyframeSheet.Section section : all)
        {
            if (section.owner() && !targetIds.contains(section.id()))
            {
                layout.put(section.id(), false);
            }
        }

        return layout;
    }

    private static boolean builtin(TrackCategory category)
    {
        return category == TrackCategory.REPLAY || category == TrackCategory.FORM;
    }

    /** Every heading the rows sit under, and the headings those sit in, in the order they stand. */
    private static Set<UIKeyframeSheet.Section> sections(List<UIKeyframeSheet> sheets)
    {
        Set<UIKeyframeSheet.Section> sections = new LinkedHashSet<>();

        for (UIKeyframeSheet sheet : sheets)
        {
            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                sections.add(section);
            }
        }

        return sections;
    }

    /** The headings with the category's rows in them. */
    private static Set<UIKeyframeSheet.Section> sectionsOf(List<UIKeyframeSheet> sheets, TrackCategory category)
    {
        Set<UIKeyframeSheet.Section> sections = new HashSet<>();

        for (UIKeyframeSheet sheet : sheets)
        {
            if (UIReplaysEditor.categoryOf(sheet) != category)
            {
                continue;
            }

            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                sections.add(section);
            }
        }

        return sections;
    }

    private static Set<String> ids(List<UIKeyframeSheet.Section> sections)
    {
        Set<String> ids = new HashSet<>();

        for (UIKeyframeSheet.Section section : sections)
        {
            ids.add(section.id());
        }

        return ids;
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
