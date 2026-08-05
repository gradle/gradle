/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.time;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * Detects slow platform timers, replacing them with a background thread that periodically updates
 * a cached timer that the application may read from quickly.
 * <p>
 * On most platforms, {@link System#nanoTime()} is a fast userspace read (~20ns). On Windows it
 * is backed by {@code QueryPerformanceCounter}, which on some machines (notably virtualized ones,
 * or when the OS has fallen back to the HPET or ACPI timer) costs tens of microseconds per call.
 * Code that reads the timer per event can disproportional amount of time doing so, leading to
 * seconds of additional accumulated execution time per build.
 * <p>
 * On platforms suspected of an expensive timer, this manager starts a background thread that
 * measures the actual cost of the platform timer on this machine. If it is inexpensive, the
 * thread exits and nothing changes. If it is expensive, the thread installs a
 * {@link CachedTimeSource} into {@link Time} and keeps its reading fresh by caching
 * the platform timer at a regular interval. Timer readings then cost a volatile load
 * regardless of the platform timer. The initial probing takes place off the critical path of
 * application startup. The platform timer remains in-place while probing takes place.
 * <p>
 * With the cached source installed, an individual reading may be stale by up to the tick interval.
 * Durations computed from pairs of readings have an error bounded by the tick interval. Sums and
 * averages over many measured durations converge on the true values. The tick interval used by this
 * manager matches the millisecond-level granularity that consumers ({@link Time#clock()} and
 * {@link Time#startTimer()}) already expose. The clock's timestamps never go backwards, including
 * across the switchover, which {@link MonotonicClock} guarantees.
 * <p>
 * This manager is a global service. Closing the owning registry stops the thread, which
 * restores direct platform readings.
 */
@ServiceScope(Scope.Global.class)
public class TimeSourceManager implements Closeable {

    private static final long DEFAULT_TICK_MILLIS = 1;

    /**
     * Above this average cost per {@link System#nanoTime()} call, the caching ticker is
     * worthwhile. A healthy timer costs ~20-40ns; a pathological one costs thousands of
     * nanoseconds or more. The threshold leaves room for interpreter overhead, since the
     * probe may run before the measuring loop is JIT compiled.
     */
    private static final long SLOW_NANO_TIME_THRESHOLD_NANOS = 1000;

    /**
     * The number of calls to {@link System#nanoTime()} to perform in a single probe batch.
     */
    private static final int PROBE_BATCH_CALLS = 1_000;

    /**
     * The maximum number of probe batches to perform when probing.
     */
    private static final int PROBE_MAX_BATCHES = 100;

    /**
     * The maximum time to spend probing.
     */
    private static final long PROBE_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

    /**
     * A sink for probe measurements. Writing the accumulated {@link System#nanoTime()} readings
     * to a volatile field prevents the JIT from eliminating the calls the probe is meant to measure.
     */
    @SuppressWarnings("unused")
    private static volatile long blackhole;

    /**
     * The cached time source that is periodically updated by the ticker thread.
     * It is only valid while the ticker is active.
     */
    private final CachedTimeSource timeSource = new CachedTimeSource();

    /**
     * The background thread that performs platform timer probing, installs the cached
     * source into {@link Time}, and periodically updates it.
     */
    private final @Nullable Thread thread;

    /**
     * True if this manager should probe the platform timer cost before installing
     * the cached source. False if the cached source should be installed immediately.
     * For testing.
     */
    private final boolean probeFirst;

    /**
     * True if the cached source should be installed into {@link Time}. False if
     * the ticker should only update the cached source. For testing.
     */
    private final boolean installIntoTime;

    /**
     * The time interval between updates of the cached source.
     */
    private final long tickIntervalMillis;

    /**
     * True while the ticker should remain running. False if this manager is not enabled
     * or has been closed.
     */
    private volatile boolean running;

    /**
     * True if the ticker is starting, started, or stopping. False otherwise. Used
     * in testing to verify the ticker starts appropriately.
     */
    private volatile boolean tickerActive;

    /**
     * Creates the manager that optionally measures the platform timer cost, and if warranted,
     * installs the cached source into {@link Time}.
     */
    public static TimeSourceManager start(boolean timerSuspectedExpensive) {
        TimeSourceManager manager = new TimeSourceManager(timerSuspectedExpensive, true, true, DEFAULT_TICK_MILLIS);
        manager.startThread();
        return manager;
    }

    /**
     * Creates a manager whose ticker runs unconditionally, without probing the timer cost or
     * installing into {@link Time}. For testing.
     */
    static TimeSourceManager startWithoutProbe(long tickIntervalMillis) {
        TimeSourceManager manager = new TimeSourceManager(true, false, false, tickIntervalMillis);
        manager.startThread();
        return manager;
    }

    /**
     * Creates a manager whose ticker runs unconditionally and installs into {@link Time},
     * without probing the timer cost. For testing on platforms with a swappable time source.
     */
    static TimeSourceManager startWithoutProbeAndInstall(long tickIntervalMillis) {
        TimeSourceManager manager = new TimeSourceManager(true, false, true, tickIntervalMillis);
        manager.startThread();
        return manager;
    }

    private TimeSourceManager(
        boolean enabled,
        boolean probeFirst,
        boolean installIntoTime,
        long tickIntervalMillis
    ) {
        this.probeFirst = probeFirst;
        this.installIntoTime = installIntoTime;
        this.tickIntervalMillis = tickIntervalMillis;
        this.running = enabled;
        this.thread = enabled ? new Thread(this::runTicker, "Cached time source ticker") : null;
    }

    /**
     * Starts the background thread that probes the performance of the platform timer,
     * installing it and periodically updating it if the platform timer is determined
     * to be expensive.
     */
    private void startThread() {
        if (thread != null) {
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Return true if the cached source is currently being updated by the ticker thread.
     * False if the timer on this machine was measured to be performant or if platform
     * timer probing has not yet finished.
     */
    public boolean isTickerActive() {
        return tickerActive && running;
    }

    /**
     * The cached time source managed by this manager. Its reading is only valid while the
     * ticker is active.
     */
    CachedTimeSource getCachedTimeSource() {
        return timeSource;
    }

    /**
     * Run the ticker thread, probing the platform timer cost and installing the cached source
     * into {@link Time} if warranted.
     */
    private void runTicker() {
        if (probeFirst && (!running || isPlatformTimerFastEnough())) {
            return;
        }

        // The source must have a valid reading before anything can observe it
        timeSource.tick();
        this.tickerActive = true;
        if (installIntoTime) {
            Time.installTimeSource(timeSource);
        }

        try {
            while (running) {
                timeSource.tick();
                // Thread.sleep rather than LockSupport.parkNanos: on Windows, the JVM raises
                // the timer resolution (per-process, since Windows 10 2004) to 1ms around
                // sub-10ms sleeps, while parkNanos waits on the default scheduler quantum
                // and would achieve a ~15.6ms tick.
                Thread.sleep(tickIntervalMillis);
            }
        } catch (InterruptedException ignored) {
            // Closed while sleeping
        } finally {
            // Restore the platform timer no matter how this thread exits, including on unexpected
            // errors, so nothing keeps reading a cache that has stopped advancing.
            if (installIntoTime) {
                Time.uninstallTimeSource();
            }
            this.tickerActive = false;
        }
    }

    /**
     * Return true if the platform timer is fast enough that the cached source is not needed.
     */
    private static boolean isPlatformTimerFastEnough() {
        if (hasUnreasonablySlowPlatformTimer()) {
            return false;
        }

        long sink = 0;

        // Warm up the call before measuring it.
        for (int i = 0; i < PROBE_BATCH_CALLS; i++) {
            sink += System.nanoTime();
        }

        long batches = 0;
        long start = System.nanoTime();
        long now = start;
        long deadline = start + PROBE_BUDGET_NANOS;
        while (now < deadline && batches < PROBE_MAX_BATCHES) {
            for (int i = 0; i < PROBE_BATCH_CALLS - 1; i++) {
                sink += System.nanoTime();
            }
            now = System.nanoTime();
            batches++;
        }
        blackhole = sink;

        long result = (now - start) / (PROBE_BATCH_CALLS * batches);
        return result <= SLOW_NANO_TIME_THRESHOLD_NANOS;
    }

    /**
     * Return true if the platform timer is much slower than reasonable. Used to detect very
     * slow timers that would otherwise cause the JIT warmup to take longer than desired.
     */
    private static boolean hasUnreasonablySlowPlatformTimer() {
        long sink = 0;
        int miniCalls = 16;
        long miniStart = System.nanoTime();
        for (int i = 0; i < miniCalls - 1; i++) {
            sink += System.nanoTime();
        }

        long stop = System.nanoTime();
        blackhole = sink;

        long miniAverage = (stop - miniStart) / miniCalls;
        return miniAverage > 10 * SLOW_NANO_TIME_THRESHOLD_NANOS;
    }

    @Override
    public void close() {
        this.running = false;
        Thread thread = this.thread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * A {@link TimeSource} whose nano time reading is read from a cached variable that is
     * periodically updated by the ticker thread. The reading is only valid once the first
     * {@link #tick()} has happened.
     */
    static class CachedTimeSource implements TimeSource {

        private volatile long nanos;

        private void tick() {
            nanos = System.nanoTime();
        }

        @Override
        public long nanoTime() {
            return nanos;
        }

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

    }

}
