package tsp.headdb;

import org.bukkit.command.PluginCommand;
import tsp.headdb.core.command.*;
import tsp.headdb.core.economy.BasicEconomyProvider;
import tsp.headdb.core.economy.VaultProvider;
import tsp.headdb.core.storage.Storage;
import tsp.headdb.core.task.UpdateTask;
import tsp.headdb.core.util.HeadDBLogger;
import tsp.nexuslib.NexusPlugin;
import tsp.nexuslib.inventory.PaneListener;
import tsp.nexuslib.localization.TranslatableLocalization;
import tsp.nexuslib.util.PluginUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.Optional;

public class HeadDB extends NexusPlugin {

    private static HeadDB instance;
    private HeadDBLogger logger;
    private Optional<TranslatableLocalization> localization = Optional.empty();
    private Optional<Storage> storage = Optional.empty();
    private Optional<BasicEconomyProvider> economyProvider = Optional.empty();
    private Optional<CommandManager> commandManager = Optional.empty();

    @Override
    public void onStart(NexusPlugin nexusPlugin) {
        instance = this;
        instance.saveDefaultConfig();
        instance.logger = new HeadDBLogger(getConfig().getBoolean("debug"));
        instance.logger.info("Loading HeadDB - " + instance.getDescription().getVersion());

        new UpdateTask(getConfig().getLong("refresh", 86400L)).schedule(this);
        instance.logger.info("Loaded " + loadLocalization() + " languages!");

        if(!getConfig().getBoolean("only-api")) {
            instance.initStorage();
            instance.initEconomy();

            new PaneListener(this);

            instance.commandManager = Optional.of(new CommandManager());
            loadCommands();
        }

        initMetrics();
        ensureLatestVersion();
        instance.logger.info("Done!");
    }

    @Override
    public void onDisable() {
        if (storage.isPresent()) {
            storage.get().getPlayerStorage().suspend();
            File langFile = new File(getDataFolder(), "langs.data");
            if (!langFile.exists()) {
                try {
                    langFile.createNewFile();
                    if(localization.isPresent())
                        localization.get().saveLanguages(langFile);
                } catch (IOException ex) {
                    logger.error("Failed to save receiver langauges!");
                    ex.printStackTrace();
                }
            }
        }
    }

    private void initMetrics() {
        Metrics metrics = new Metrics(this, 9152);

        metrics.addCustomChart(new Metrics.SimplePie("economy_provider", () -> {
            if (getEconomyProvider().isPresent()) {
                return this.getConfig().getString("economy.provider");
            }

            return "None";
        }));
    }

    private void ensureLatestVersion() {
        PluginUtils.isLatestVersion(this, 84967, latest -> {
            if (Boolean.FALSE.equals(latest)) {
                instance.logger.warning("There is a new update available for HeadDB on spigot!");
                instance.logger.warning("Download: https://www.spigotmc.org/resources/84967");
            }
        });
    }

    // Loaders

    private void initStorage() {
        storage = Optional.of(new Storage(getConfig().getInt("storage.threads")));
        storage.get().getPlayerStorage().init();
    }

    private int loadLocalization() {
        instance.localization = Optional.of(new TranslatableLocalization(this, "messages"));
        try {
            instance.localization.get().createDefaults();
            int count = instance.localization.get().load();
            File langFile = new File(getDataFolder(), "langs.data");
            if (langFile.exists()) {
                localization.get().loadLanguages(langFile);
            }

            return count;
        } catch (URISyntaxException | IOException ex) {
            instance.logger.error("Failed to load localization!");
            ex.printStackTrace();
            this.setEnabled(false);
            return 0;
        }
    }

    private void initEconomy() {
        if (!getConfig().getBoolean("economy.enabled")) {
            instance.logger.debug("Economy disabled by config.yml!");
            instance.economyProvider = Optional.empty();
            return;
        }

        String raw = getConfig().getString("economy.provider", "VAULT");
        if (raw.equalsIgnoreCase("VAULT")) {
            economyProvider = Optional.of(new VaultProvider());
        }

        economyProvider.ifPresent(BasicEconomyProvider::init);
        instance.logger.info("Economy Provider: " + raw);
    }

    private void loadCommands() {
        PluginCommand main = getCommand("headdb");
        if (main != null) {
            var mainCommand = new CommandMain();
            main.setExecutor(mainCommand);
            main.setTabCompleter(mainCommand);
            localization.ifPresent(localization ->
                    main.setPermissionMessage(localization.getConsoleMessage("noPermission").orElse("No Permissions!")));
        } else {
            instance.logger.error("Could not find main 'headdb' command!");
            this.setEnabled(false);
            return;
        }

        new CommandHelp().register();
        new CommandCategory().register();
        new CommandSearch().register();
        new CommandGive().register();
        new CommandUpdate().register();
        new CommandReload().register();
        new CommandTexture().register();
        new CommandLanguage().register();
        new CommandSettings().register();
        new CommandInfo().register();
    }

    // Getters

    public Optional<Storage> getStorage() {
        return storage;
    }

    public Optional<CommandManager> getCommandManager() {
        return commandManager;
    }

    public Optional<BasicEconomyProvider> getEconomyProvider() {
        return economyProvider;
    }

    @SuppressWarnings("DataFlowIssue")
    private DecimalFormat decimalFormat = new DecimalFormat(getConfig().getString("economy.format"));

    public DecimalFormat getDecimalFormat() {
        return decimalFormat != null ? decimalFormat : (decimalFormat = new DecimalFormat("##.##"));
    }

    public TranslatableLocalization getLocalization() {
        return localization.orElseThrow();
    }

    public HeadDBLogger getLog() {
        return logger;
    }

    public static HeadDB getInstance() {
        return instance;
    }

}
