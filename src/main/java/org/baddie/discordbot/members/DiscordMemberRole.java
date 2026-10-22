package org.baddie.discordbot.members;

public enum DiscordMemberRole {
    SEMI_SWEET("1379369453917310997", "полусладенькие"),
    SWEET("998350106023698432", "сладенькие"),
    MINISTER_OF_DODEP("1393889005698940949", "министр додепа"),
    MOMMY("1012647364256870411", "mommy"),
    BITCH("1312424009387868231", "сучка");

    private final String id;
    private final String displayName;

    DiscordMemberRole(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}
