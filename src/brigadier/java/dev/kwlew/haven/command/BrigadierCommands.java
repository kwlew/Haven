package dev.kwlew.haven.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Loaded only on Paper versions with the lifecycle Brigadier command API. */
public final class BrigadierCommands implements CommandRegistrar {

    public BrigadierCommands() {}

    @Override
    public void register(JavaPlugin plugin, SetHomeCommand setHome, HomeCommand home,
                         DelHomeCommand delHome, RenameHomeCommand renameHome,
                         ShareHomeCommand shareHome, UnshareHomeCommand unshareHome,
                         HomesCommand homes, HavenCommand haven,
                         HomeSuggestions suggestions) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            SuggestionProvider<CommandSourceStack> homeNames = (context, builder) -> {
                if (context.getSource().getSender() instanceof Player player) {
                    suggestions.names(player, builder.getRemaining()).forEach(builder::suggest);
                }
                return builder.buildFuture();
            };

            // /home also takes owner:home, and ':' is not allowed in an unquoted word() or string(),
            // so the argument has to be greedy; it is split back into arguments in the executor.
            SuggestionProvider<CommandSourceStack> visitableHomes = (context, builder) -> {
                if (context.getSource().getSender() instanceof Player player) {
                    suggestions.visitable(player, builder.getRemaining()).forEach(builder::suggest);
                }
                return builder.buildFuture();
            };
            SuggestionProvider<CommandSourceStack> sharedHomes = (context, builder) -> {
                if (context.getSource().getSender() instanceof Player player) {
                    suggestions.sharedHomes(player, builder.getRemaining()).forEach(builder::suggest);
                }
                return builder.buildFuture();
            };
            SuggestionProvider<CommandSourceStack> otherPlayers = (context, builder) -> {
                if (context.getSource().getSender() instanceof Player player) {
                    suggestions.otherPlayers(player, builder.getRemaining()).forEach(builder::suggest);
                }
                return builder.buildFuture();
            };
            SuggestionProvider<CommandSourceStack> recipients = (context, builder) -> {
                if (context.getSource().getSender() instanceof Player player) {
                    suggestions.recipients(player, StringArgumentType.getString(context, "home"),
                            builder.getRemaining()).forEach(builder::suggest);
                }
                return builder.buildFuture();
            };

            registrar.register(root(CommandSpec.SET_HOME)
                    .executes(context -> success(setHome.execute(
                            context.getSource().getSender(), new String[0])))
                    .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> success(setHome.execute(
                                    context.getSource().getSender(),
                                    new String[]{StringArgumentType.getString(context, "name")}))))
                    .build(), CommandSpec.SET_HOME.description, CommandSpec.SET_HOME.aliases);

            registrar.register(root(CommandSpec.HOME)
                    .executes(context -> success(home.execute(
                            context.getSource().getSender(), new String[0])))
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                            .suggests(visitableHomes)
                            .executes(context -> {
                                CommandSender sender = context.getSource().getSender();
                                String[] words = StringArgumentType.getString(context, "name")
                                        .trim().split("\\s+");

                                // No usage fallback in Brigadier, unlike the Bukkit path.
                                if (words.length != 1) {
                                    sender.sendMessage(CommandSpec.HOME.usage);
                                    return 0;
                                }
                                return success(home.execute(sender, words));
                            }))
                    .build(), CommandSpec.HOME.description, CommandSpec.HOME.aliases);

            registrar.register(root(CommandSpec.DEL_HOME)
                    .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(homeNames)
                            .executes(context -> success(delHome.execute(
                                    context.getSource().getSender(),
                                    new String[]{StringArgumentType.getString(context, "name")}))))
                    .build(), CommandSpec.DEL_HOME.description, CommandSpec.DEL_HOME.aliases);

            registrar.register(root(CommandSpec.RENAME_HOME)
                    .then(Commands.argument("old", StringArgumentType.word())
                            .suggests(homeNames)
                            .then(Commands.argument("new", StringArgumentType.word())
                                    .executes(context -> success(renameHome.execute(
                                            context.getSource().getSender(),
                                            new String[]{StringArgumentType.getString(context, "old"),
                                                    StringArgumentType.getString(context, "new")})))))
                    .build(), CommandSpec.RENAME_HOME.description, CommandSpec.RENAME_HOME.aliases);

            registrar.register(root(CommandSpec.SHARE_HOME)
                    .then(Commands.argument("home", StringArgumentType.word())
                            .suggests(homeNames)
                            .executes(context -> success(shareHome.execute(
                                    context.getSource().getSender(),
                                    new String[]{StringArgumentType.getString(context, "home")})))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(otherPlayers)
                                    .executes(context -> success(shareHome.execute(
                                            context.getSource().getSender(),
                                            new String[]{StringArgumentType.getString(context, "home"),
                                                    StringArgumentType.getString(context, "player")})))))
                    .build(), CommandSpec.SHARE_HOME.description, CommandSpec.SHARE_HOME.aliases);

            registrar.register(root(CommandSpec.UNSHARE_HOME)
                    .then(Commands.argument("home", StringArgumentType.word())
                            .suggests(sharedHomes)
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(recipients)
                                    .executes(context -> success(unshareHome.execute(
                                            context.getSource().getSender(),
                                            new String[]{StringArgumentType.getString(context, "home"),
                                                    StringArgumentType.getString(context, "player")})))))
                    .build(), CommandSpec.UNSHARE_HOME.description, CommandSpec.UNSHARE_HOME.aliases);

            registrar.register(root(CommandSpec.HOMES)
                    .executes(context -> success(homes.execute(
                            context.getSource().getSender(), new String[0])))
                    .then(Commands.literal("list")
                            .executes(context -> success(homes.execute(
                                    context.getSource().getSender(), new String[]{"list"}))))
                    .build(), CommandSpec.HOMES.description, CommandSpec.HOMES.aliases);

            SuggestionProvider<CommandSourceStack> playerNames = (context, builder) -> {
                haven.suggestPlayers(builder.getRemaining()).forEach(builder::suggest);
                return builder.buildFuture();
            };
            // Only an online target's homes are in memory; completion never reads a file.
            SuggestionProvider<CommandSourceStack> targetHomes = (context, builder) -> {
                haven.suggestHomes(StringArgumentType.getString(context, "player"),
                                builder.getRemaining())
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

            registrar.register(root(CommandSpec.HAVEN)
                    .executes(context -> run(haven, context.getSource()))
                    .then(Commands.literal("help")
                            .executes(context -> run(haven, context.getSource(), "help")))
                    .then(Commands.literal("version")
                            .requires(source -> source.getSender().hasPermission(AdminCommand.VERSION_PERMISSION))
                            .executes(context -> run(haven, context.getSource(), "version")))
                    .then(Commands.literal("reload")
                            .requires(source -> source.getSender().hasPermission(ReloadCommand.PERMISSION))
                            .executes(context -> run(haven, context.getSource(), "reload")))
                    .then(Commands.literal("homes")
                            .requires(source -> source.getSender().hasPermission(AdminCommand.HOMES_PERMISSION))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(playerNames)
                                    .executes(context -> run(haven, context.getSource(), "homes",
                                            StringArgumentType.getString(context, "player")))))
                    .then(Commands.literal("tp")
                            .requires(source -> source.getSender().hasPermission(AdminCommand.TP_PERMISSION))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(playerNames)
                                    .then(Commands.argument("home", StringArgumentType.word())
                                            .suggests(targetHomes)
                                            .executes(context -> run(haven, context.getSource(), "tp",
                                                    StringArgumentType.getString(context, "player"),
                                                    StringArgumentType.getString(context, "home"))))))
                    .then(Commands.literal("delhome")
                            .requires(source -> source.getSender().hasPermission(AdminCommand.DELHOME_PERMISSION))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(playerNames)
                                    .then(Commands.argument("home", StringArgumentType.word())
                                            .suggests(targetHomes)
                                            .executes(context -> run(haven, context.getSource(), "delhome",
                                                    StringArgumentType.getString(context, "player"),
                                                    StringArgumentType.getString(context, "home"))))))
                    .build(), CommandSpec.HAVEN.description, CommandSpec.HAVEN.aliases);
        });
    }

    /**
     * A command with no permission (the {@code /haven} root, open because {@code help} is) gets no
     * {@code requires} at all, rather than one that always passes.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> root(CommandSpec spec) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(spec.name);

        return spec.permission == null
                ? literal
                : literal.requires(source -> spec.permits(source.getSender()));
    }

    private static int run(HavenCommand haven, CommandSourceStack source, String... args) {
        return success(haven.execute(source.getSender(), args));
    }

    private static int success(boolean handled) {
        return handled ? 1 : 0;
    }
}
