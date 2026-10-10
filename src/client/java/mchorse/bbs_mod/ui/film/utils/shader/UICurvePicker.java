package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderMenu;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Picks what a curve clip animates, from one panel: the world (time of day, weather, brightness and
 * the like) and, with a shader pack on, the pack's own options.
 *
 * <ul>
 *     <li>A search field on top looks through all of it at once, sub-screens included; a hit
 *     says where it lives.</li>
 *     <li>The world's curves sit under it and stay in sight while the pack's menu is browsed.</li>
 *     <li>The pack's options menu follows as Iris lays it out, one screen at a time, with the way
 *     back up as breadcrumbs. A link with no curves behind it is dimmed, and the curvable options
 *     the menu leaves out get a page of their own at the end of the main screen.</li>
 * </ul>
 *
 * <p>A click adds a curve and leaves the panel open for the next one; an added curve reads as a
 * picked row, and a second click takes the track out again. The panel keeps one size whichever screen is
 * shown, so it doesn't jump while browsing.</p>
 */
public class UICurvePicker extends UIOverlayPanel
{
    private static final int PADDING = 6;
    private static final int MARGIN = 4;
    private static final int COLUMN_WIDTH = 210;
    private static final int ICONS_WIDTH = 20;
    private static final int TITLE_HEIGHT = 20;
    private static final int SEARCH_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 16;
    private static final int MAX_WIDTH = 680;
    private static final int MAX_MENU_HEIGHT = 300;

    /** The picker's own page with the curvable options the pack's menu leaves out */
    private static final String NOT_IN_MENU = "#not_in_menu";

    private final List<WorldCurve> world;
    private final ShaderMenu menu;
    private final Map<String, String> lang;
    private final Set<String> added;
    private final Consumer<String> onAdd;
    private final Consumer<String> onRemove;
    private final int columns;

    /** Option ids of the "not in the menu" page, in the order they're shown */
    private final List<String> notInMenu = new ArrayList<>();
    private final List<Entry> index = new ArrayList<>();

    /** Screen ids from the main screen down to the one on show */
    private final List<String> path = new ArrayList<>();
    private String query = "";

    public final UITextbox search;

    private final UIElement worldGrid;
    private final Header header;
    private final UIScrollView view;
    private final GridResizer viewGrid;

    /**
     * @param menu the shader pack's menu, or null when there's no pack to show
     * @param existing ids of the channels the clip has already
     * @param onAdd receives the channel id of every curve picked
     * @param onRemove receives the channel id of every added curve clicked again
     */
    public UICurvePicker(List<WorldCurve> world, ShaderMenu menu, Map<String, String> lang, Collection<String> existing, Consumer<String> onAdd, Consumer<String> onRemove)
    {
        super(UIKeys.CAMERA_PANELS_PICK_KEY);

        this.world = world;
        this.menu = menu;
        this.lang = lang;
        this.added = new HashSet<>(existing);
        this.onAdd = onAdd;
        this.onRemove = onRemove;
        this.columns = menu == null ? 2 : Math.max(2, menu.mainScreen.columnCount);

        this.search = new UITextbox(100, this::filter);
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.content).xy(PADDING, PADDING).w(1F, -PADDING * 2).h(SEARCH_HEIGHT);

        this.worldGrid = new UIElement();
        this.worldGrid.relative(this.content).w(1F).h(this.worldHeight());
        this.worldGrid.grid(MARGIN).items(this.columns).padding(PADDING).height(UICurveCell.HEIGHT);

        this.header = new Header();
        this.header.callback = this::goTo;
        this.header.relative(this.content).w(1F).h(HEADER_HEIGHT);

        this.view = new UIScrollView();
        this.view.relative(this.content).w(1F);
        this.view.scroll.scrollSpeed = 20;
        this.viewGrid = this.view.grid(MARGIN);
        this.viewGrid.padding(PADDING).height(UICurveCell.HEIGHT);

        this.content.add(this.search, this.worldGrid, this.header, this.view);

        if (menu != null)
        {
            this.notInMenu.addAll(menu.curvableNotInMenu);
            this.notInMenu.sort((a, b) -> this.optionLabel(a).compareToIgnoreCase(this.optionLabel(b)));
        }

        this.buildIndex();
        this.path.add(ShaderMenu.MAIN_SCREEN);
        this.refresh();
    }

    /* === Sizing (passed to UIOverlay.addOverlay by the caller) === */

    public int preferredWidth()
    {
        int content = this.columns * COLUMN_WIDTH + (this.columns - 1) * MARGIN + PADDING * 2;

        return Math.min(MAX_WIDTH, content + ICONS_WIDTH);
    }

    /** Fits the tallest screen of the menu, so browsing doesn't make the panel jump. */
    public int preferredHeight()
    {
        int height = TITLE_HEIGHT + PADDING + SEARCH_HEIGHT + this.worldHeight() + HEADER_HEIGHT;
        int menu = PADDING;

        if (this.menu != null)
        {
            ShaderMenu.Screen main = this.menu.mainScreen;

            menu = gridHeight(this.notInMenu.size(), this.columns);
            menu = Math.max(menu, gridHeight(main.cells.size() + (this.notInMenu.isEmpty() ? 0 : 1), main.columnCount));

            for (ShaderMenu.Screen screen : this.menu.subScreens.values())
            {
                menu = Math.max(menu, gridHeight(screen.cells.size(), screen.columnCount));
            }

            menu = Math.min(MAX_MENU_HEIGHT, menu);
        }

        return height + menu;
    }

    private int worldHeight()
    {
        return gridHeight(this.world.size(), this.columns);
    }

    private static int gridHeight(int count, int columns)
    {
        columns = Math.max(1, columns);

        int rows = Math.max(1, (count + columns - 1) / columns);

        return rows * UICurveCell.HEIGHT + (rows - 1) * MARGIN + PADDING * 2;
    }

    /* === State === */

    private boolean isSearching()
    {
        return !this.query.isEmpty();
    }

    private void filter(String text)
    {
        this.query = text.trim().toLowerCase(Locale.ROOT);

        this.refresh();
    }

    private void open(String screenId)
    {
        if (NOT_IN_MENU.equals(screenId) || this.menu.getScreen(screenId) != null)
        {
            this.path.add(screenId);
            this.refresh();
        }
    }

    /** A breadcrumb was clicked: back up to that screen. */
    private void goTo(int depth)
    {
        while (this.path.size() > depth + 1)
        {
            this.path.remove(this.path.size() - 1);
        }

        this.refresh();
    }

    /** A curve cell was clicked: adds the track, or takes it out if the clip has it already. */
    private void toggle(String channelId, UICurveCell cell)
    {
        if (this.added.remove(channelId))
        {
            this.onRemove.accept(channelId);
            cell.added(false);
        }
        else
        {
            this.added.add(channelId);
            this.onAdd.accept(channelId);
            cell.added(true);
        }
    }

    /** Enter while searching adds the first hit that isn't in the clip yet. */
    @Override
    public void confirm()
    {
        if (!this.isSearching())
        {
            return;
        }

        for (UICurveCell cell : this.view.getChildren(UICurveCell.class))
        {
            if (cell.kind == UICurveCell.Kind.CURVE && !cell.isAdded())
            {
                cell.activate();

                return;
            }
        }
    }

    /* === Building the parts === */

    private void refresh()
    {
        boolean searching = this.isSearching();
        int y = PADDING + SEARCH_HEIGHT;

        this.worldGrid.setVisible(!searching);

        if (searching)
        {
            y += MARGIN;
        }
        else
        {
            /* The grid's own padding keeps it off the search field */
            this.worldGrid.y(y);
            y += this.worldHeight();
        }

        this.header.y(y);
        y += HEADER_HEIGHT;
        this.view.y(y).h(1F, -y);

        this.worldGrid.removeAll();

        for (WorldCurve curve : this.world)
        {
            this.worldGrid.add(this.worldCell(curve));
        }

        this.view.removeAll();

        if (searching)
        {
            this.fillResults();
        }
        else
        {
            this.fillScreen();
        }

        this.view.scroll.scrollTo(0);

        if (this.hasParent())
        {
            this.resize();
        }
    }

    private void fillScreen()
    {
        if (this.menu == null)
        {
            this.header.crumbs(Collections.singletonList(UIKeys.CAMERA_PANELS_CURVES_NO_SHADERS.get()), true);

            return;
        }

        List<String> crumbs = new ArrayList<>();

        for (String screenId : this.path)
        {
            crumbs.add(this.screenTitle(screenId));
        }

        this.header.crumbs(crumbs, false);

        String screenId = this.path.get(this.path.size() - 1);

        if (NOT_IN_MENU.equals(screenId))
        {
            this.viewGrid.items(this.columns);

            for (String optionId : this.notInMenu)
            {
                this.view.add(this.optionCurveCell(optionId));
            }

            return;
        }

        ShaderMenu.Screen screen = this.menu.getScreen(screenId);

        if (screen == null)
        {
            return;
        }

        this.viewGrid.items(Math.max(1, screen.columnCount));

        for (ShaderMenu.Cell cell : screen.cells)
        {
            this.view.add(this.menuCell(cell));
        }

        if (screen == this.menu.mainScreen && !this.notInMenu.isEmpty())
        {
            this.view.add(this.linkCell(NOT_IN_MENU, this.notInMenu.size()));
        }
    }

    private void fillResults()
    {
        int found = 0;

        this.viewGrid.items(1);

        for (Entry entry : this.index)
        {
            if (entry.matches(this.query))
            {
                UICurveCell cell = this.curveCell(entry.channelId, entry.label).icon(entry.icon).note(entry.where);

                cell.tooltip(IKey.constant(entry.tooltip));
                this.view.add(cell);
                found += 1;
            }
        }

        IKey title = found == 0 ? UIKeys.CAMERA_PANELS_CURVES_NOT_FOUND : UIKeys.CAMERA_PANELS_CURVES_FOUND.format(found);

        this.header.crumbs(Collections.singletonList(title.get()), true);
    }

    private UICurveCell curveCell(String channelId, String label)
    {
        return UICurveCell.curve(label, (cell) -> this.toggle(channelId, cell)).added(this.added.contains(channelId));
    }

    private UICurveCell worldCell(WorldCurve curve)
    {
        UICurveCell cell = this.curveCell(curve.id, curve.label.get()).icon(curve.icon);

        if (curve.color != null)
        {
            cell.swatch(curve.color.getAsInt());
        }
        else if (curve.value != null)
        {
            Double value = curve.value.get();

            if (value != null)
            {
                cell.note(UINumericInput.format(value));
            }
        }

        cell.tooltip(curve.label);

        return cell;
    }

    private UICurveCell optionCurveCell(String optionId)
    {
        String label = this.optionLabel(optionId);
        UICurveCell cell = this.curveCell(CurveClip.SHADER_CURVES_PREFIX + optionId, label);

        cell.tooltip(IKey.constant(label + " (" + optionId + ")"));

        return cell;
    }

    private UICurveCell menuCell(ShaderMenu.Cell cell)
    {
        if (cell.type == ShaderMenu.CellType.OPTION && cell.optionId != null)
        {
            String label = this.optionLabel(cell.optionId);
            UICurveCell element = cell.curvable ? this.curveCell(CurveClip.SHADER_CURVES_PREFIX + cell.optionId, label) : UICurveCell.option(label);

            element.note(this.optionValue(cell));
            element.tooltip(IKey.constant(label + " (" + cell.optionId + ")"));

            return element;
        }
        else if (cell.type == ShaderMenu.CellType.LINK && cell.targetScreenId != null)
        {
            return this.linkCell(cell.targetScreenId, this.countCurves(cell.targetScreenId));
        }
        else if (cell.type == ShaderMenu.CellType.PROFILE)
        {
            String profile = cell.profileName == null ? UIKeys.CAMERA_PANELS_SHADER_CUSTOM.get() : this.lang.getOrDefault("profile." + cell.profileName, cell.profileName);

            return UICurveCell.option(UIKeys.CAMERA_PANELS_SHADER_PROFILE.get()).note(profile);
        }

        return UICurveCell.empty();
    }

    private UICurveCell linkCell(String screenId, int curves)
    {
        return UICurveCell.link(this.screenTitle(screenId), (cell) -> this.open(screenId)).note("›").muted(curves == 0);
    }

    /** How many curvable options can be reached from a screen, its sub-screens included. */
    private int countCurves(String screenId)
    {
        Set<String> options = new HashSet<>();

        this.collectCurves(screenId, new HashSet<>(), options);

        return options.size();
    }

    private void collectCurves(String screenId, Set<String> screens, Set<String> options)
    {
        ShaderMenu.Screen screen = this.menu.getScreen(screenId);

        if (screen == null || !screens.add(screenId))
        {
            return;
        }

        for (ShaderMenu.Cell cell : screen.cells)
        {
            if (cell.type == ShaderMenu.CellType.OPTION && cell.curvable && cell.optionId != null)
            {
                options.add(cell.optionId);
            }
            else if (cell.type == ShaderMenu.CellType.LINK && cell.targetScreenId != null)
            {
                this.collectCurves(cell.targetScreenId, screens, options);
            }
        }
    }

    /* === Search index === */

    private void buildIndex()
    {
        String worldTitle = UIKeys.CAMERA_PANELS_CURVES_WORLD.get();

        for (WorldCurve curve : this.world)
        {
            String label = curve.label.get();

            this.index.add(new Entry(curve.id, label, curve.id, worldTitle, curve.icon, label));
        }

        if (this.menu == null)
        {
            return;
        }

        this.indexScreen(ShaderMenu.MAIN_SCREEN, this.screenTitle(ShaderMenu.MAIN_SCREEN), new HashSet<>(), new HashSet<>());

        String notInMenuTitle = this.screenTitle(NOT_IN_MENU);

        for (String optionId : this.notInMenu)
        {
            this.indexOption(optionId, notInMenuTitle);
        }
    }

    /**
     * Index the curvable options of a screen and the screens under it. An option shown on several
     * screens is listed once, where it's met first.
     *
     * @param where the screen's place for a hit to show: the pack's name for the main screen, the
     *              path of titles below it for the rest
     */
    private void indexScreen(String screenId, String where, Set<String> screens, Set<String> options)
    {
        ShaderMenu.Screen screen = this.menu.getScreen(screenId);

        if (screen == null || !screens.add(screenId))
        {
            return;
        }

        for (ShaderMenu.Cell cell : screen.cells)
        {
            if (cell.type == ShaderMenu.CellType.OPTION && cell.curvable && cell.optionId != null && options.add(cell.optionId))
            {
                this.indexOption(cell.optionId, where);
            }
        }

        for (ShaderMenu.Cell cell : screen.cells)
        {
            if (cell.type == ShaderMenu.CellType.LINK && cell.targetScreenId != null)
            {
                String title = this.screenTitle(cell.targetScreenId);

                this.indexScreen(cell.targetScreenId, screen == this.menu.mainScreen ? title : where + " › " + title, screens, options);
            }
        }
    }

    private void indexOption(String optionId, String where)
    {
        String label = this.optionLabel(optionId);

        this.index.add(new Entry(CurveClip.SHADER_CURVES_PREFIX + optionId, label, optionId, where, null, label + " (" + optionId + ")"));
    }

    /* === Labels === */

    private String screenTitle(String screenId)
    {
        if (screenId == null || screenId.isEmpty())
        {
            return this.menu.name.isEmpty() ? UIKeys.CAMERA_PANELS_CURVES_SHADER.get() : this.menu.name;
        }

        if (NOT_IN_MENU.equals(screenId))
        {
            return UIKeys.CAMERA_PANELS_CURVES_NOT_IN_MENU.get();
        }

        return this.lang.getOrDefault("screen." + screenId, screenId);
    }

    private String optionLabel(String optionId)
    {
        return this.lang.getOrDefault("option." + optionId, optionId);
    }

    private String optionValue(ShaderMenu.Cell cell)
    {
        if (cell.booleanOption)
        {
            return "true".equals(cell.currentValue) ? UIKeys.CAMERA_PANELS_SHADER_ON.get() : UIKeys.CAMERA_PANELS_SHADER_OFF.get();
        }

        /* String option: prefix + localized value + suffix, mirroring Iris' StringElementWidget */
        String value = cell.currentValue == null ? "" : cell.currentValue;
        String prefix = this.lang.getOrDefault("prefix." + cell.optionId, "");
        String suffix = this.lang.getOrDefault("suffix." + cell.optionId, "");
        String localized = this.lang.getOrDefault("value." + cell.optionId + "." + value, value);

        return prefix + localized + suffix;
    }

    /**
     * A curve of the world rather than of a shader pack; it works with shaders off too.
     */
    public static class WorldCurve
    {
        public final String id;
        public final IKey label;
        public final Icon icon;

        /** The world's current value in the curve's own units, for the value box */
        public Supplier<Double> value;

        /** For a colour curve: the current colour, shown as a swatch instead of a value */
        public IntSupplier color;

        public WorldCurve(String id, IKey label, Icon icon)
        {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }

        public WorldCurve value(Supplier<Double> value)
        {
            this.value = value;

            return this;
        }

        public WorldCurve color(IntSupplier color)
        {
            this.color = color;

            return this;
        }
    }

    private static class Entry
    {
        public final String channelId;
        public final String label;
        public final String where;
        public final Icon icon;
        public final String tooltip;

        private final String text;

        public Entry(String channelId, String label, String id, String where, Icon icon, String tooltip)
        {
            this.channelId = channelId;
            this.label = label;
            this.where = where;
            this.icon = icon;
            this.tooltip = tooltip;
            this.text = (label + "\n" + id).toLowerCase(Locale.ROOT);
        }

        public boolean matches(String query)
        {
            return this.text.contains(query);
        }
    }

    /**
     * A section's title line. With several crumbs it's the path down the pack's menu, and every
     * crumb but the last takes back up to its screen; a muted one is a plain grey note.
     */
    private static class Header extends UIElement
    {
        private static final int GAP = 6;
        private static final String SEPARATOR = "›";

        public IntConsumer callback;

        private final List<String> crumbs = new ArrayList<>();
        private boolean muted;

        /* Found during render, so a click knows what's under it */
        private int hovered = -1;

        public void crumbs(List<String> crumbs, boolean muted)
        {
            this.crumbs.clear();
            this.crumbs.addAll(crumbs);
            this.muted = muted;
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.hovered >= 0 && this.callback != null && this.area.isInside(context))
            {
                UIUtils.playClick();
                this.callback.accept(this.hovered);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            boolean inside = this.area.isInside(context);
            int x = this.area.x + PADDING;
            int textY = this.area.my(font.getHeight());
            int last = this.crumbs.size() - 1;

            this.hovered = -1;

            for (int i = 0; i <= last; i++)
            {
                if (i > 0)
                {
                    context.batcher.text(SEPARATOR, x, textY, Colors.GRAY);
                    x += font.getWidth(SEPARATOR) + GAP;
                }

                String label = this.crumbs.get(i);

                if (i == last)
                {
                    label = font.limitToWidth(label, this.area.ex() - PADDING - x);
                }

                int w = font.getWidth(label);
                boolean hover = inside && !this.muted && i < last && context.mouseX >= x - GAP / 2 && context.mouseX < x + w + GAP / 2;
                int color = this.muted ? Colors.GRAY : (i == last ? Colors.WHITE : (hover ? Colors.LIGHTEST_GRAY : Colors.LIGHTER_GRAY));

                if (hover)
                {
                    this.hovered = i;
                }

                context.batcher.textShadow(label, x, textY, color);
                x += w + GAP;
            }

            if (this.hovered >= 0)
            {
                context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            }

            super.render(context);
        }
    }
}
