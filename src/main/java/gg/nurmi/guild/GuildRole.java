package gg.nurmi.guild;

public enum GuildRole {
    OWNER, OFFICER, MEMBER;

    public boolean canManageMembers() {
        return this == OWNER || this == OFFICER;
    }

    public boolean canManageSettings() {
        return this == OWNER;
    }
}
