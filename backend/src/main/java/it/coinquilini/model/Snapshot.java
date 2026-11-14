package it.coinquilini.model;

import java.util.List;

/** Contratto models.ts: i moduli non ancora implementati restituiscono liste vuote. */
public record Snapshot(
        PublicUser user, House house, List<Member> members,
        List<?> expenses, List<?> repayments, List<?> items, List<?> frequent,
        List<?> tasks, List<?> turns, List<?> notices,
        long myBalance, List<?> settlements, List<PublicUser> adminUsers, List<House> adminHouses) {
    public static Snapshot forHouse(User user, House house, List<Member> members) {
        return new Snapshot(PublicUser.from(user), house, members,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                0, List.of(), List.of(), List.of());
    }
}
