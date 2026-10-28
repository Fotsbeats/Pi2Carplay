package org.bench.boxhome;

final class LayoutRules {
    static final int COLUMNS = 6;
    static final int PAGE_SIZE = COLUMNS * 2;
    static boolean isDoubleTap(long previous,long now,int timeout) {
        return previous>=0 && now>=previous && now-previous<=timeout;
    }
    static int localRailWidth(int displayWidth, int contentLeft) {
        return Math.max(0, Math.min(104, Math.max(0, displayWidth)) - Math.max(0, contentLeft));
    }
    static int pages(int extras) { return 1 + (Math.max(0, extras) + PAGE_SIZE - 1) / PAGE_SIZE; }
    static int clamp(int index, int count) { return Math.max(0, Math.min(count - 1, index)); }
    static int snap(int startPage, float movement, int width, int count) {
        if (width <= 0) return 0;
        int step = Math.abs(movement) > width * .12f ? (movement > 0 ? 1 : -1) : 0;
        return clamp(startPage + step, count);
    }
}
