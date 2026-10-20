package com.github.kalsupes.raidparty;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Receipt times are local; remote clocks cannot extend an offline deadline. */
final class PartyPresence {
    static final Duration STALE_AFTER = Duration.ofSeconds(60);
    static final Duration LEAVE_AFTER = Duration.ofMinutes(15);
    enum State { WAITING, ONLINE, OFFLINE, STALE, EXPIRED }

    private static final class Member {
        Instant lastSeen;
        Instant offlineSince;
        boolean received;
        boolean expired;
        boolean heartbeatExpected;
        Member(Instant now) { lastSeen = now; }
    }

    private final Map<Long, Member> members = new HashMap<>();

    synchronized void clear() { members.clear(); }
    synchronized void remove(long id) { members.remove(id); }
    synchronized void joined(long id, Instant now) { members.put(id, new Member(now)); }

    synchronized State state(long id, Instant now) {
        Member member = members.computeIfAbsent(id, ignored -> new Member(now));
        Instant since = member.offlineSince == null ? member.lastSeen : member.offlineSince;
        if ((member.offlineSince != null || !member.received || member.heartbeatExpected)
                && expiredSince(since, now)) member.expired = true;
        if (member.expired) return State.EXPIRED;
        if (member.offlineSince != null) return State.OFFLINE;
        if (!member.received) return State.WAITING;
        return member.heartbeatExpected && !now.isBefore(member.lastSeen.plus(STALE_AFTER)) ? State.STALE : State.ONLINE;
    }

    /** Null means a legacy delta, which cannot undo an explicit logout. */
    synchronized State receive(long id, Boolean online, Instant now) {
        return receive(id, online, now, true);
    }

    synchronized State receive(long id, Boolean online, Instant now, boolean heartbeatExpected) {
        if (state(id, now) == State.EXPIRED) return State.EXPIRED;
        Member member = members.get(id);
        member.heartbeatExpected |= heartbeatExpected;
        if (Boolean.FALSE.equals(online)) {
            if (member.offlineSince == null) member.offlineSince = now;
        } else if (Boolean.TRUE.equals(online) || member.offlineSince == null) {
            member.offlineSince = null;
            member.lastSeen = now;
            member.received = true;
        }
        return state(id, now);
    }

    static boolean expiredSince(Instant since, Instant now) {
        return since != null && !now.isBefore(since.plus(LEAVE_AFTER));
    }
}
