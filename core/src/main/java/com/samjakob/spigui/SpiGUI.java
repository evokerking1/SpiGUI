package com.samjakob.spigui;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import com.samjakob.spigui.menu.SGMenu;
import com.samjakob.spigui.menu.SGMenuListenerBase;
import com.samjakob.spigui.menu.SGOpenMenu;
import com.samjakob.spigui.toolbar.SGDefaultToolbarBuilderFactory;
import com.samjakob.spigui.toolbar.SGToolbarBuilder;

/**
 * The core class for the SpiGUI library.
 *
 * <p>One instance of the SpiGUI class is registered for each plugin using it.
 *
 * <p>The expected usage of SpiGUI is that you register a SpiGUI instance for your plugin with <code>new SpiGUI(this);
 * </code> in your class that extends <code>JavaPlugin</code>. You can then use the instance you've created throughout
 * your project to create GUIs that use SpiGUI.
 */
public final class SpiGUI {

    /**
     * The qualified name of the InitializeSpiGUI class.
     *
     * <p>This class is included in a {@link SpiGUI} distribution and is initialized once for each "installation" of
     * SpiGUI to set up version-specific structures and factories.
     */
    private static final String INITIALIZER_CLASS = "com.samjakob.spigui.InitializeSpiGUI";

    /**
     * The qualified name of the class for the SGMenuListener.
     *
     * <p>This class is initialized for each instance of {@link SpiGUI}.
     */
    private static final String LISTENER_CLASS = "com.samjakob.spigui.menu.SGMenuListener";

    static {
        ensureFactoriesInitialized();
    }

    /** The plugin that owns this instance of SpiGUI. */
    @Nonnull
    private final JavaPlugin plugin;

    /**
     * Whether to cancel inventory click actions by default.
     *
     * <p>This is typically set to true so events needn't be manually cancelled every time an item is clicked in the
     * inventory as that is the behavior most typically used with an inventory GUI.
     *
     * <p>With this set to true, you can of course use <code>event.setCancelled(false);</code> (or <code>
     * event.setResult(Event.Result.DEFAULT);</code>) in your button listeners to allow the default behavior of the
     * inventory to take place.
     */
    private boolean blockDefaultInteractions = true;

    /**
     * Whether automatic pagination should be enabled.
     *
     * <p>This is set to true by default, and it means if you set an inventory slot greater than the highest slot on the
     * inventory, a row will automatically be added containing pagination items that allow a user to scroll between
     * different 'pages' to access all the assigned slots in the inventory.
     *
     * <p>This concept is based on an improved version of the approach taken with my SpigotPaginatedGUI library.
     */
    private boolean enableAutomaticPagination = true;

    /**
     * The defaultToolbarBuilder is the plugin-wide {@link SGToolbarBuilder} called when building pagination buttons for
     * inventory GUIs.
     *
     * <p>This can be overridden per-inventory, as well as per-plugin using the appropriate methods on either the
     * inventory class ({@link SGMenu}) or your plugin's instance of {@link SpiGUI}.
     */
    private SGToolbarBuilder defaultToolbarBuilder =
            SGDefaultToolbarBuilderFactory.get().newToolbarBuilder();

    /**
     * Creates an instance of the SpiGUI library associated with a given plugin.
     *
     * <p>This is intended to be stored as a static field in your plugin with a public static getter (or a public static
     * field - dealer's choice) and you create inventories through this class by calling {@link #create(String, int)} on
     * the static {@link SpiGUI} field.
     *
     * <h4>Design Notes</h4>
     *
     * <p>The association with a plugin is an important design decision that was overlooked in this library's
     * predecessor, SpigotPaginatedGUI.
     *
     * <p>This library is not designed to act as a standalone plugin because that is inconvenient for both developers
     * and server administrators for such a relatively insignificant task - the library is more just a small convenience
     * measure. However, this library still needs to register a listener under a given plugin, which is where the issue
     * arises; which plugin should the library use to register events with. Previously, it was whichever plugin made the
     * call to <code>PaginatedGUI.prepare</code> first, however this obviously causes problems if that particular plugin
     * is unloaded - as any other plugins using the library no longer have the listener that was registered.
     *
     * <p>This approach was therefore considered a viable compromise - each plugin registers its own listener, however
     * the downside of this is that each inventory and the listener must now also be registered with the plugin too.
     *
     * <p>Thus, the design whereby this class is registered as a static field on a {@link JavaPlugin} instance and
     * serves as a proxy for creating ({@link SGMenu}) inventories and an instance of the {@code SGMenuListener}
     * registered with that plugin seemed like a good way to try and minimize the inconvenience of the approach.
     *
     * @param plugin The plugin using SpiGUI.
     */
    public SpiGUI(@Nonnull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "SpiGUI needs to be registered under a plugin.");

        try {
            final Class<? extends SGMenuListenerBase> listenerClass =
                    Class.forName(LISTENER_CLASS).asSubclass(SGMenuListenerBase.class);
            final SGMenuListenerBase listener =
                    listenerClass.getDeclaredConstructor(SpiGUI.class).newInstance(this);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        } catch (ClassNotFoundException
                | ClassCastException
                | NoSuchMethodException
                | InvocationTargetException
                | InstantiationException
                | IllegalAccessException ex) {
            throw new RuntimeException(String.format(
                    "Failed to locate and register a valid %s for this Spigot version (%s).",
                    LISTENER_CLASS, Bukkit.getVersion()));
        }
    }

    /**
     * An alias for {@link #create(String, int, String)} with the tag set to null. Use this method if you don't need the
     * tag, or you don't know what it's for.
     *
     * <p>The rows parameter is used in place of the size parameter of the Bukkit/Spigot inventory API. So, if you
     * wanted an inventory of size 27, you would supply 3 as the value of the <code>rows</code> parameter.
     *
     * <p>The <code>name</code> parameter supports the following 'placeholders':
     *
     * <ul>
     *   <li><code>{currentPage}</code>: the current page the inventory is on.
     *   <li><code>{maxPage}</code>: the final page of the inventory.
     * </ul>
     *
     * @param name The display name of the inventory.
     * @param rows The number of rows the inventory should have per page.
     * @return The created inventory.
     */
    public SGMenu create(String name, int rows) {
        return create(name, rows, null);
    }

    /**
     * Creates an inventory with a given name, tag and number of rows. The display name is color code translated.
     *
     * <p>The <code>name</code> parameter supports the following 'placeholders':
     *
     * <ul>
     *   <li><code>{currentPage}</code>: the current page the inventory is on.
     *   <li><code>{maxPage}</code>: the final page of the inventory.
     * </ul>
     *
     * <p>The rows parameter is used in place of the size parameter of the Bukkit/Spigot inventory API. So, if you
     * wanted an inventory of size 27, you would supply 3 as the value of the <code>rows</code> parameter.
     *
     * <p>The tag is used when getting all open inventories ({@link #findOpenWithTag(String)}) with your chosen tag. An
     * example of where this might be useful is with a permission GUI - when the permissions are updated by one user in
     * the GUI, it would be desirable to refresh the state of the permissions GUI for all users observing the GUI.
     *
     * <p>You might give the permissions GUI a tag of 'myPermissionsGUI', then refreshing all the open instances of the
     * GUI would be as simple as getting all open inventories with the aforementioned tag using
     * {@link #findOpenWithTag(String)} and calling refresh on each GUI in the list.
     *
     * @param name The display name of the inventory.
     * @param rows The number of rows the inventory should have per page.
     * @param tag The inventory's tag.
     * @return The created inventory.
     */
    public SGMenu create(String name, int rows, String tag) {
        return new SGMenu(this, name, rows, tag);
    }

    /**
     * Returns the plugin that this instance of SpiGUI was registered with.
     *
     * @return the plugin for this SpiGUI instance.
     */
    @Nonnull
    public JavaPlugin getOwner() {
        return this.plugin;
    }

    /**
     * Whether default inventory interactions should be cancelled.
     *
     * @param blockDefaultInteractions Whether default inventory interactions should be cancelled.
     * @see SpiGUI#blockDefaultInteractions
     */
    public void setBlockDefaultInteractions(boolean blockDefaultInteractions) {
        this.blockDefaultInteractions = blockDefaultInteractions;
    }

    /**
     * Returns the value of {@link SpiGUI#blockDefaultInteractions} for this plugin.
     *
     * @return Whether default inventory interactions should be cancelled.
     */
    public boolean areDefaultInteractionsBlocked() {
        return blockDefaultInteractions;
    }

    /**
     * Whether automatic pagination should be enabled.
     *
     * @param enableAutomaticPagination Whether automatic pagination should be enabled.
     * @see SpiGUI#enableAutomaticPagination
     */
    public void setEnableAutomaticPagination(boolean enableAutomaticPagination) {
        this.enableAutomaticPagination = enableAutomaticPagination;
    }

    /**
     * Returns the value of {@link SpiGUI#enableAutomaticPagination} for this plugin.
     *
     * @return Whether automatic pagination is enabled.
     */
    public boolean isAutomaticPaginationEnabled() {
        return enableAutomaticPagination;
    }

    /**
     * The default toolbar builder used for GUIs.
     *
     * @param defaultToolbarBuilder The default toolbar builder used for GUIs.
     * @see SpiGUI#defaultToolbarBuilder
     */
    public void setDefaultToolbarBuilder(SGToolbarBuilder defaultToolbarBuilder) {
        this.defaultToolbarBuilder = defaultToolbarBuilder;
    }

    /**
     * The default toolbar builder used for GUIs.
     *
     * @return The default toolbar builder used for GUIs.
     * @see SpiGUI#defaultToolbarBuilder
     */
    public SGToolbarBuilder getDefaultToolbarBuilder() {
        return defaultToolbarBuilder;
    }

    /**
     * Finds a list of all open inventories with a given tag along with the player who has that inventory open.
     *
     * <p>This returns a list of {@link SGOpenMenu} which simply stores the opened inventory along with the player
     * viewing the open inventory.
     *
     * <p>Supplying null as the tag value will get all untagged inventories.
     *
     * @param tag The tag to search for.
     * @return A list of {@link SGOpenMenu} whose inventories have the specified tag.
     */
    public List<SGOpenMenu> findOpenWithTag(String tag) {

        List<SGOpenMenu> foundInventories = new ArrayList<>();

        // Loop through every online player...
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // ...if that player has an open inventory with a top inventory...
            if (player.getOpenInventory().getTopInventory() != null) {
                // ...get that top inventory.
                Inventory topInventory = player.getOpenInventory().getTopInventory();

                // If the top inventory is an SGMenu,
                if (topInventory.getHolder() != null && topInventory.getHolder() instanceof SGMenu) {
                    // and the SGMenu has the tag matching the one we're checking for,
                    SGMenu inventory = (SGMenu) topInventory.getHolder();
                    if (Objects.equals(inventory.getTag(), tag))
                        // add the SGMenu to our list of found inventories.
                        foundInventories.add(new SGOpenMenu(inventory, player));
                }
            }
        }

        return foundInventories;
    }

    /**
     * Loads the {@code InitializeSpiGUI} class from the version-specific wrapper package that includes the core.
     *
     * <p>This allows version-specific implementations to be loaded into factories.
     */
    public static void ensureFactoriesInitialized() {
        try {
            // This is sufficient for loading the class and having the static initialization blocks run.
            Class.forName(INITIALIZER_CLASS);
        } catch (ClassNotFoundException ignored) {
            throw new RuntimeException(String.format(
                    "Failed to locate and register a valid %s for this Spigot version (%s).",
                    INITIALIZER_CLASS, Bukkit.getVersion()));
        }
    }
}
