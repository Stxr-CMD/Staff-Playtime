package com.staffplaytime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Sends the messages from config.yml.
 * Every message can use <prefix> and any placeholder you pass in.
 */
public class Messages {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private FileConfiguration cfg;
    private Component prefix = Component.empty();

    public Messages(FileConfiguration cfg) {
        reload(cfg);
    }

    public void reload(FileConfiguration cfg) {
        this.cfg = cfg;
        this.prefix = mm.deserialize(cfg.getString("prefix", "<dark_red><bold>STAFF PLAYTIME</bold></dark_red> <dark_gray>»</dark_gray> "));
    }

    /** Turns any MiniMessage text into a component, with <prefix> available. */
    public Component parse(String text, TagResolver... resolvers) {
        TagResolver prefixTag = TagResolver.resolver("prefix", Tag.selfClosingInserting(prefix));
        return mm.deserialize(text, TagResolver.resolver(prefixTag, TagResolver.resolver(resolvers)));
    }

    public Component get(String key, TagResolver... resolvers) {
        return parse(cfg.getString("messages." + key, "<red>Missing message: " + key), resolvers);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(get(key, resolvers));
    }

    /** For messages that are a list in the config (help, check). */
    public void sendList(CommandSender to, String key, TagResolver... resolvers) {
        List<String> lines = cfg.getStringList("messages." + key);
        for (String line : lines) {
            to.sendMessage(parse(line, resolvers));
        }
    }
}
