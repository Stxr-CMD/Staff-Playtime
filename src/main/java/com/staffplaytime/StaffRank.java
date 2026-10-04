package com.staffplaytime;

/**
 * One staff rank from the "ranks" section of config.yml.
 *
 * id         = the key in the config (owner, admin, ...), this is what we save in the database
 * display    = the name shown to players
 * color      = MiniMessage color tag, like "<red>"
 * permission = permission node that makes someone this rank (LuckPerms: group.<name>)
 * weight     = higher number = higher rank
 */
public record StaffRank(String id, String display, String color, String permission, int weight) {
}
