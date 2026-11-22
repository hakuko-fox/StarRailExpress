package org.agmas.noellesroles.game.roles.vtuber;

/** v2.1 的纯数值规则，供运行逻辑和边界测试共用。 */
public final class VtuberV21Rules {
    public static final int MAX_STONE_ENERGY = 2000;
    private VtuberV21Rules() {}
    public static int stoneEnergy(int energy, boolean petrified, int ticks) {
        return (int) Math.max(0L, Math.min(MAX_STONE_ENERGY, (long) energy + (petrified ? -4L : 1L) * ticks));
    }
    public static int taskGoal(int players) { return (players * 7 + 9) / 10; }
    public static int weaponCooldown(int ticks, String role, boolean evil, boolean decoyHit) {
        if (decoyHit || (role.equals("ayers") && evil)) return 0;
        return role.equals("yuzu_fengling") ? Math.round(ticks * 0.7F) : ticks;
    }
    public static long fatalDeadline(long current, long now, boolean gun) {
        long candidate = now + fatalDelayTicks(gun);
        return current == 0 ? candidate : Math.min(current, candidate);
    }
    public static int fatalDelayTicks(boolean gun) { return (gun ? 10 : 15) * 20; }
}
