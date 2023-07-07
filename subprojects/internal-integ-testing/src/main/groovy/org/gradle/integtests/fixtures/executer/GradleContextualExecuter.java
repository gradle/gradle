/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.fixtures.executer;

import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeoutInterceptor;
import org.gradle.test.fixtures.file.TestDirectoryProvider;

import java.nio.charset.Charset;
import java.util.Locale;

import static org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout.DEFAULT_TIMEOUT_SECONDS;

/**
 * Selects a different executer implementation based on the value of a system property.
 *
 * Facilitates running the same test in different execution modes.
 */
public class GradleContextualExecuter extends AbstractDelegatingGradleExecuter {

    private static final String EXECUTER_SYS_PROP = "org.gradle.integtest.executer";

    private Executer executerType;

    private enum Executer {
        embedded(false),
        forking(true),
        noDaemon(true),
        parallel(true, true),
        configCache(true),
        isolatedProjects(true);

        final public boolean forks;
        final public boolean executeParallel;

        Executer(boolean forks) {
            this(forks, false);
        }

        Executer(boolean forks, boolean parallel) {
            this.forks = forks;
            this.executeParallel = parallel;
        }
    }

    private static Executer getSystemPropertyExecuter() {
        return Executer.valueOf(System.getProperty(EXECUTER_SYS_PROP, Executer.forking.toString()));
    }

    public static boolean isEmbedded() {
        return !getSystemPropertyExecuter().forks;
    }

    public static boolean isNoDaemon() {
        return getSystemPropertyExecuter() == Executer.noDaemon;
    }

    public static boolean isDaemon() {
        return !(isNoDaemon() || isEmbedded());
    }

    public static boolean isLongLivingProcess() {
        return !isNoDaemon();
    }

    public static boolean isParallel() {
        return getSystemPropertyExecuter().executeParallel;
    }

    public static boolean isNotConfigCache() {
        return !isConfigCache();
    }

    public static boolean isConfigCache() {
        return getSystemPropertyExecuter() == Executer.configCache;
    }

    private GradleExecuter gradleExecuter;

    public GradleContextualExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, IntegrationTestBuildContext buildContext) {
        super(distribution, testDirectoryProvider, buildContext);
        this.executerType = getSystemPropertyExecuter();
    }

    @Override
    protected GradleExecuter configureExecuter() {
        if (!getClass().desiredAssertionStatus()) {
            throw new RuntimeException("Assertions must be enabled when running integration tests.");
        }

        if (gradleExecuter == null) {
            gradleExecuter = createExecuter(executerType);
        } else {
            gradleExecuter.reset();
        }
        configureExecuter(gradleExecuter);
        try {
            gradleExecuter.assertCanExecute();
        } catch (AssertionError assertionError) {
            if (gradleExecuter instanceof InProcessGradleExecuter) {
                throw new RuntimeException("Running tests with a Gradle distribution in embedded mode is no longer supported.", assertionError);
            }
            gradleExecuter = new NoDaemonGradleExecuter(getDistribution(), getTestDirectoryProvider());
            configureExecuter(gradleExecuter);
        }

        return gradleExecuter;
    }

    private void configureExecuter(GradleExecuter gradleExecuter) {
        copyTo(gradleExecuter);
    }

    private GradleExecuter createExecuter(Executer executerType) {
        switch (executerType) {
            case embedded:
                return new InProcessGradleExecuter(getDistribution(), getTestDirectoryProvider(), gradleVersion, buildContext);
            case noDaemon:
                return new NoDaemonGradleExecuter(getDistribution(), getTestDirectoryProvider(), gradleVersion, buildContext);
            case parallel:
                return new ParallelForkingGradleExecuter(getDistribution(), getTestDirectoryProvider(), gradleVersion, buildContext);
            case forking:
                return new DaemonGradleExecuter(getDistribution(), getTestDirectoryProvider(), gradleVersion, buildContext);
            case configCache:
                return new ConfigurationCacheGradleExecuter(getDistribution(), getTestDirectoryProvider(), gradleVersion, buildContext);
            case isolatedProjects:
                return new IsolatedProjectsGradleExecuter(getDistribution(), getTestDirectoryProvider(), gradleVersion, buildContext);
            default:
                throw new RuntimeException("Not a supported executer type: " + executerType);
        }
    }

    @Override
    public void cleanup() {
        new IntegrationTestTimeoutInterceptor(DEFAULT_TIMEOUT_SECONDS).intercept(ignored -> {
            if (gradleExecuter != null) {
                gradleExecuter.stop();
            }
            GradleContextualExecuter.super.cleanup();
        });

    }

    @Override
    public GradleExecuter ignoreCleanupAssertions() {
        if (gradleExecuter != null) {
            gradleExecuter.ignoreCleanupAssertions();
        }
        return super.ignoreCleanupAssertions();
    }

    @Override
    public GradleExecuter reset() {
        if (gradleExecuter != null) {
            gradleExecuter.reset();
        }
        return super.reset();
    }

    // The following overrides are here instead of in 'InProcessGradleExecuter' due to the way executors are layered+inherited
    // This should be improved as part of https://github.com/gradle/gradle-private/issues/1009

    @Override
    public GradleExecuter withDefaultCharacterEncoding(String defaultCharacterEncoding) {
        if (executerType == Executer.embedded && !Charset.forName(defaultCharacterEncoding).equals(Charset.defaultCharset())) {
            // need to fork to apply the new default character encoding
            requireDaemon().requireIsolatedDaemons();
        }
        return super.withDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    @Override
    public GradleExecuter withDefaultLocale(Locale defaultLocale) {
        if (executerType == Executer.embedded && !defaultLocale.equals(Locale.getDefault())) {
            // need to fork to apply the new default locale
            requireDaemon().requireIsolatedDaemons();
        }
        return super.withDefaultLocale(defaultLocale);
    }
}
