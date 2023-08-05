package tsp.headdb;

import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.Nullable;
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
import java.util.Objects;
import java.util.Optional;

public class HeadDB extends NexusPlugin {

    private static HeadDB instance;
    private HeadDBLogger logger;
    private @Nullable TranslatableLocalization localization;
    private @Nullable Storage storage;
    private @Nullable BasicEconomyProvider economyProvider;
    private @Nullable CommandManager commandManager;

    @Override
    public void onStart(NexusPlugin nexusPlugin) {
        instance = this;
        saveDefaultConfig();
        logger = new HeadDBLogger(getConfig().getBoolean("debug"));
        logger.info("Loading HeadDB - " + getDescription().getVersion());

        new UpdateTask(getConfig().getLong("refresh", 86400L)).schedule(this);
        logger.info("Loaded " + loadLocalization() + " languages!");

        if(!getConfig().getBoolean("only-api")) {
            initStorage();
            initEconomy();

            new PaneListener(this);

            commandManager = new CommandManager();
            loadCommands();
        } else
            Objects.requireNonNull(getCommand("headdb")).setUsage("Commands are disabled!");

        initMetrics();
        ensureLatestVersion();
        logger.info("Done!");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.getPlayerStorage().suspend();
            File langFile = new File(getDataFolder(), "langs.data");
            if (!langFile.exists()) {
                try {
                    langFile.createNewFile();
                    if(localization != null)
                        localization.saveLanguages(langFile);
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
                logger.warning("There is a new update available for HeadDB on spigot!");
                logger.warning("Download: https://www.spigotmc.org/resources/84967");
            }
        });
    }

    // Loaders

    private void initStorage() {
        storage = new Storage(getConfig().getInt("storage.threads"));
        storage.getPlayerStorage().init();
    }

    private int loadLocalization() {
        localization = new TranslatableLocalization(this, "messages");
        try {
            localization.createDefaults();
            int count = localization.load();
            File langFile = new File(getDataFolder(), "langs.data");
            if (langFile.exists()) {
                localization.loadLanguages(langFile);
            }

            return count;
        } catch (URISyntaxException | IOException ex) {
            logger.error("Failed to load localization!");
            ex.printStackTrace();
            this.setEnabled(false);
            return 0;
        }
    }

    private void initEconomy() {
        if (!getConfig().getBoolean("economy.enabled")) {
            logger.debug("Economy disabled by config.yml!");
            economyProvider = null;
            return;
        }

        String raw = getConfig().getString("economy.provider", "VAULT");
        if (raw.equalsIgnoreCase("VAULT")) {
            economyProvider = new VaultProvider();
        }

        if(economyProvider != null) {
            economyProvider.init();
            logger.info("Economy Provider: " + raw);
        } else
            logger.info("No Economy Provider found.");
    }

    private void loadCommands() {
        PluginCommand main = getCommand("headdb");
        if (main != null) {
            var mainCommand = new CommandMain();
            main.setExecutor(mainCommand);
            main.setTabCompleter(mainCommand);
            if(localization != null)
                    main.setPermissionMessage(localization.getConsoleMessage("noPermission").orElse("No Permissions!"));
        } else {
            logger.error("Could not find main 'headdb' command!");
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
        return Optional.ofNullable(storage);
    }

    public Optional<CommandManager> getCommandManager() {
        return Optional.ofNullable(commandManager);
    }

    public Optional<BasicEconomyProvider> getEconomyProvider() {
        return Optional.ofNullable(economyProvider);
    }

    @SuppressWarnings("DataFlowIssue")
    private DecimalFormat decimalFormat = new DecimalFormat(getConfig().getString("economy.format"));

    public DecimalFormat getDecimalFormat() {
        return decimalFormat != null ? decimalFormat : (decimalFormat = new DecimalFormat("##.##"));
    }

    public TranslatableLocalization getLocalization() {
        Objects.requireNonNull(localization);
        return localization;
    }

    public HeadDBLogger getLog() {
        return logger;
    }

    public static HeadDB getInstance() {
        return instance;
    }

}
