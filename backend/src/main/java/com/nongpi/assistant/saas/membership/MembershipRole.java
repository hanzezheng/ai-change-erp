package com.nongpi.assistant.saas.membership;

public enum MembershipRole {
    OWNER,
    ADMIN,
    STAFF;

    public boolean atLeast(MembershipRole required) {
        return ordinal() <= required.ordinal();
    }
}
