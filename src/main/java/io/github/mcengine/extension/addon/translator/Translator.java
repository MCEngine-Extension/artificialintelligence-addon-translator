package io.github.mcengine.extension.addon.translator;

import io.github.mcengine.api.core.MCEngineCoreApi;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.api.artificialintelligence.extension.addon.IMCEngineArtificialIntelligenceAddOn;

import io.github.mcengine.extension.addon.translator.TranslatorCommand;
import io.github.mcengine.extension.addon.translator.TranslatorListener;
import io.github.mcengine.extension.addon.translator.TranslatorTabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Main class for the Translator AddOn.
 * <p>
 * Registers the /aiaddonexample command and event listeners.
 */
public class Translator implements IMCEngineArtificialIntelligenceAddOn {

    /**
     * Initializes the Translator AddOn.
     * Called automatically by the MCEngine core plugin.
     *
     * @param plugin The Bukkit plugin instance.
     */
    @Override
    public void onLoad(Plugin plugin) {
        /**
         * Logger instance for the Translator AddOn.
         */
        MCEngineExtensionLogger logger = new MCEngineExtensionLogger(plugin, "AddOn", "Translator");

        try {
            // Register event listener
            PluginManager pluginManager = Bukkit.getPluginManager();
            pluginManager.registerEvents(new TranslatorListener(plugin), plugin);

            // Reflectively access Bukkit's CommandMap
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Define the /aiaddonexample command
            Command aiAddonExampleCommand = new Command("aiaddonexample") {

                /**
                 * Handles command execution for /aiaddonexample.
                 */
                private final TranslatorCommand handler = new TranslatorCommand();

                /**
                 * Handles tab-completion for /aiaddonexample.
                 */
                private final TranslatorTabCompleter completer = new TranslatorTabCompleter();

                /**
                 * Executes the /aiaddonexample command.
                 *
                 * @param sender The command sender.
                 * @param label  The command label.
                 * @param args   The command arguments.
                 * @return true if successful.
                 */
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handler.onCommand(sender, this, label, args);
                }

                /**
                 * Handles tab-completion for the /aiaddonexample command.
                 *
                 * @param sender The command sender.
                 * @param alias  The alias used.
                 * @param args   The current arguments.
                 * @return A list of possible completions.
                 */
                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return completer.onTabComplete(sender, this, alias, args);
                }
            };

            aiAddonExampleCommand.setDescription("AI AddOn example command.");
            aiAddonExampleCommand.setUsage("/aiaddonexample");

            // Dynamically register the /aiaddonexample command
            commandMap.register(plugin.getName().toLowerCase(), aiAddonExampleCommand);

            logger.info("Enabled successfully.");
        } catch (Exception e) {
            logger.warning("Failed to initialize Translator AddOn: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisload(Plugin plugin) {
        // No specific unload logic
    }

    @Override
    public void setId(String id) {
        MCEngineCoreApi.setId("mcengine-translator");
    }
}
