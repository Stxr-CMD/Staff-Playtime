package com.staffplaytime.storage;

import java.util.UUID;

/**
 * One staff member with all of their playtime numbers (in seconds).
 * This is what the GUI and the /check command show.
 */
public class StaffEntry {

    public final UUID uuid;
    public String name;
    public String rankId;
    public long thisWeek;
    public long lastWeek;
    public long total;

    public StaffEntry(UUID uuid, String name, String rankId) {
        this.uuid = uuid;
        this.name = name;
        this.rankId = rankId;
    }
}
