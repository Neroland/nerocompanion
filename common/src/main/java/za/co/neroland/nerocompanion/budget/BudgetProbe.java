package za.co.neroland.nerocompanion.budget;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Server tick-cost probe behind {@code /nscomp budget}. Companion tick code wraps its work in
 * {@link #start()} / {@link #stop(long)}; the command reports the rolling average per companion and in
 * total. Holds only counters: no entity ids, owners or positions, so nothing personal is ever kept or
 * printed. Server thread only; the counters are atomic so a report can be read from any thread.
 */
public final class BudgetProbe {

    /** Length of one reporting window, in server ticks. */
    public static final int WINDOW_TICKS = 100;

    private static final AtomicLong WINDOW_NANOS = new AtomicLong();
    private static final AtomicLong WINDOW_SAMPLES = new AtomicLong();
    private static final AtomicLong WINDOW_TICKS_SEEN = new AtomicLong();
    private static volatile Report last = new Report(0, 0, 0);

    private BudgetProbe() { }

    /** A finished window: average ms per server tick for all companions, the sample count, and ticks covered. */
    public record Report(double totalMsPerTick, long companionTicks, long serverTicks) {
        /** Average ms one companion tick costs, or 0 when nothing ticked. */
        public double msPerCompanionTick() {
            return companionTicks == 0 ? 0 : (totalMsPerTick * serverTicks) / companionTicks;
        }
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void stop(long startNanos) {
        WINDOW_NANOS.addAndGet(System.nanoTime() - startNanos);
        WINDOW_SAMPLES.incrementAndGet();
    }

    /** Called once at the end of every server tick by each loader. */
    public static void endServerTick() {
        if (WINDOW_TICKS_SEEN.incrementAndGet() < WINDOW_TICKS) return;
        long ticks = WINDOW_TICKS_SEEN.getAndSet(0);
        long nanos = WINDOW_NANOS.getAndSet(0);
        long samples = WINDOW_SAMPLES.getAndSet(0);
        last = new Report(nanos / 1_000_000.0 / ticks, samples, ticks);
    }

    public static Report lastReport() {
        return last;
    }
}
