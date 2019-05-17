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

import org.gradle.api.Action;
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeoutInterceptor;
import org.gradle.test.fixtures.file.TestDirectoryProvider;

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
        parallel(true, true);

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
            default:
                throw new RuntimeException("Not a supported executer type: " + executerType);
        }
    }

    @Override
    public void cleanup() {
        new IntegrationTestTimeoutInterceptor(DEFAULT_TIMEOUT_SECONDS).intercept(new Action<Void>() {
            @Override
            public void execute(Void ignored) {
                if (gradleExecuter != null) {
                    gradleExecuter.stop();
                }
                GradleContextualExecuter.super.cleanup();
            }
        });

    }

    @Override
    public GradleExecuter reset() {
        if (gradleExecuter != null) {
            gradleExecuter.reset();
        }
        return super.reset();
    }
}
