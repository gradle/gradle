/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.server.health;

import com.google.common.base.Joiner;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.util.NumberUtil;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.DO_NOT_EXPIRE;
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE;
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.IMMEDIATE_EXPIRE;
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.highestPriorityOf;

/**
 * A {@link DaemonExpirationStrategy} which monitors daemon health and expires the daemon
 * whenever unhealthy conditions are detected. Currently, this strategy monitors JVM memory
 * health by detecting GC thrashing and excessive heap or metaspace usage. In addition to
 * expiring the daemon, whenever unhealthy conditions are detected, this strategy will
 * print a warning log to the console informing the user of the issue and instructing them
 * on how to adjust daemon memory settings.
 */
@ServiceScope(Scope.Global.class)
public class HealthExpirationStrategy implements DaemonExpirationStrategy {

    /**
     * A system property which enables this strategy. Defaults to true.
     */
    public static final String ENABLE_PERFORMANCE_MONITORING = "org.gradle.daemon.performance.enable-monitoring";

    /**
     * A system property which disables logging upon expiration events. Defaults to false.
     */
    public static final String DISABLE_PERFORMANCE_LOGGING = "org.gradle.daemon.performance.disable-logging";

    /**
     * The prefix for the message logged when an unhealthy condition is detected.
     * Used to strip this message from the logs during integration testing.
     */
    public static final String EXPIRE_DAEMON_MESSAGE = "The Daemon will expire ";

    /**
     * Used to determine if a status of a given severity has already been logged.
     * We use this to ensure we don't print the same warning multiple times to the user
     * if {@link #checkExpiration()} is called multiple times while an unhealthy
     * memory condition persists.
     */
    private DaemonExpirationStatus mostSevereStatus = DO_NOT_EXPIRE;
    private final Lock statusLock = new ReentrantLock();

    private final DaemonHealthStats stats;
    private final GarbageCollectorMonitoringStrategy strategy;
    private final Logger logger;

    public HealthExpirationStrategy(DaemonHealthStats stats, GarbageCollectorMonitoringStrategy strategy) {
        this(stats, strategy, LoggerFactory.getLogger(HealthExpirationStrategy.class));
    }

    HealthExpirationStrategy(DaemonHealthStats stats, GarbageCollectorMonitoringStrategy strategy, Logger logger) {
        this.stats = stats;
        this.strategy = strategy;
        this.logger = logger;
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        // We cannot check this in the constructor since system properties are copied to the daemon after initialization.
        if (!Boolean.parseBoolean(System.getProperty(ENABLE_PERFORMANCE_MONITORING, "true"))) {
            return DaemonExpirationResult.NOT_TRIGGERED;
        }

        DaemonExpirationStatus expirationStatus = DO_NOT_EXPIRE;
        List<String> reasons = new ArrayList<>();

        GarbageCollectionStats heapStats = stats.getHeapStats();
        if (heapStats.isValid() && heapStats.getEventCount() >= 5
            && strategy.isAboveHeapUsageThreshold(heapStats.getUsedPercent())
        ) {
            if (strategy.isAboveGcThrashingThreshold(heapStats.getGcRate())) {
                reasons.add("since the JVM garbage collector is thrashing");
                expirationStatus = highestPriorityOf(IMMEDIATE_EXPIRE, expirationStatus);
            } else if (strategy.isAboveGcRateThreshold(heapStats.getGcRate())) {
                reasons.add("after running out of JVM heap space");
                expirationStatus = highestPriorityOf(GRACEFUL_EXPIRE, expirationStatus);
            }
        }

        GarbageCollectionStats nonHeapStats = stats.getNonHeapStats();
        if (nonHeapStats.isValid() && nonHeapStats.getEventCount() >= 5
            && strategy.isAboveNonHeapUsageThreshold(nonHeapStats.getUsedPercent())
        ) {
            reasons.add("after running out of JVM Metaspace");
            expirationStatus = highestPriorityOf(GRACEFUL_EXPIRE, expirationStatus);
        }

        if (expirationStatus == DO_NOT_EXPIRE) {
            return DaemonExpirationResult.NOT_TRIGGERED;
        }

        // We've encountered an unhealthy condition. Log if necessary.

        String reason = Joiner.on(" and ").join(reasons);
        if (shouldPrintLog(expirationStatus)) {

            String when = expirationStatus == GRACEFUL_EXPIRE ? "after the build" : "immediately";
            String extraInfo = expirationStatus == GRACEFUL_EXPIRE
                ? "The daemon will restart for the next build, which may increase subsequent build times"
                : "The memory settings for this project must be adjusted to avoid this failure";

            String maxHeap = heapStats.isValid() ? NumberUtil.formatBytes(heapStats.getMaxSizeInBytes()) : "unknown";
            String maxMetaspace = nonHeapStats.isValid() ? NumberUtil.formatBytes(nonHeapStats.getMaxSizeInBytes()) : "unknown";
            String url = new DocumentationRegistry().getDocumentationRecommendationFor("information on how to set these values", "build_environment", "sec:configuring_jvm_memory");

            logger.warn(EXPIRE_DAEMON_MESSAGE + when + " " + reason + ".\n"
                + "The project memory settings are likely not configured or are configured to an insufficient value.\n"
                + extraInfo + ".\n"
                + "These settings can be adjusted by setting 'org.gradle.jvmargs' in 'gradle.properties'.\n"
                + "The currently configured max heap space is '" + maxHeap + "' and the configured max metaspace is '" + maxMetaspace + "'.\n"
                + url + "\n"
                + "To disable this warning, set '" + DISABLE_PERFORMANCE_LOGGING + "=true'.");
        }

        logger.debug("Daemon health: {}", stats.getHealthInfo());

        return new DaemonExpirationResult(expirationStatus, reason);
    }

    private boolean shouldPrintLog(DaemonExpirationStatus newStatus) {
        if (Boolean.getBoolean(DISABLE_PERFORMANCE_LOGGING)) {
            return false;
        }

        statusLock.lock();
        try {
            DaemonExpirationStatus previous = mostSevereStatus;
            mostSevereStatus = highestPriorityOf(previous, newStatus);
            return previous != mostSevereStatus;
        } finally {
            statusLock.unlock();
        }
    }

}
