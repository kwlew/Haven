package dev.kwlew.haven.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Loaded only on Paper versions with the lifecycle Brigadier command API. */
public final class BrigadierCommands implements CommandRegistrar {

    public BrigadierCommands() {}

    @Override
    public void register(JavaPlugin plugin, SetHomeCommand setHome, HomeCommand home,
                         DelHomeCommand delHome, HomesCommand homes, ReloadCommand reload,
                         HomeSuggestions suggestions) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            SuggestionProvider<CommandSourceStack> homeNames = (context, builder) -> {
                if (context.getSource().getSender() instanceof Player player) {
                    suggestions.names(player, builder.getRemaining()).forEach(builder::suggest);
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
                    .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(homeNames)
                            .executes(context -> success(home.execute(
                                    context.getSource().getSender(),
                                    new String[]{StringArgumentType.getString(context, "name")}))))
                    .build(), CommandSpec.HOME.description, CommandSpec.HOME.aliases);

            registrar.register(root(CommandSpec.DEL_HOME)
                    .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(homeNames)
                            .executes(context -> success(delHome.execute(
                                    context.getSource().getSender(),
                                    new String[]{StringArgumentType.getString(context, "name")}))))
                    .build(), CommandSpec.DEL_HOME.description, CommandSpec.DEL_HOME.aliases);

            registrar.register(root(CommandSpec.HOMES)
                    .executes(context -> success(homes.execute(
                            context.getSource().getSender(), new String[0])))
                    .build(), CommandSpec.HOMES.description, CommandSpec.HOMES.aliases);

            registrar.register(root(CommandSpec.HAVEN)
                    .then(Commands.literal("reload")
                            .executes(context -> success(reload.execute(
                                    context.getSource().getSender(), new String[]{"reload"}))))
                    .build(), CommandSpec.HAVEN.description, CommandSpec.HAVEN.aliases);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(CommandSpec spec) {
        return Commands.literal(spec.name)
                .requires(source -> source.getSender().hasPermission(spec.permission));
    }

    private static int success(boolean handled) {
        return handled ? 1 : 0;
    }
}
