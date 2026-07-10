package gg.nurmi.guild;

public enum GuildRole {
    OWNER, OFFICER, MEMBER;

    public boolean canManageMembers() {
        return this == OWNER || this == OFFICER;
    }

    public boolean canManageSettings() {
        return this == OWNER;
    }

    // messages.yml key holding this role's translated, colored label (e.g. "guild.role-owner").
    public String translationKey() {
        return "guild.role-" + name().toLowerCase();
    }
}
