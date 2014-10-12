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

import org.gradle.test.fixtures.file.TestDirectoryProvider;

/**
 * Selects a different executer implementation based on the value of a system property.
 *
 * Facilitates running the same test in different execution modes.
 */
public class GradleContextualExecuter extends AbstractDelegatingGradleExecuter {

    private static final String EXECUTER_SYS_PROP = "org.gradle.integtest.executer";
    private static final String UNKNOWN_OS_SYS_PROP = "org.gradle.integtest.unknownos";

    private Executer executerType;

    private static enum Executer {
        embedded(false),
        forking(true),
        daemon(true),
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

    public static boolean isDaemon() {
        return getSystemPropertyExecuter() == Executer.daemon;
    }

    public static boolean isParallel() {
        return getSystemPropertyExecuter().executeParallel;
    }

    public GradleContextualExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
        this.executerType = getSystemPropertyExecuter();
    }

    protected GradleExecuter configureExecuter() {
        if (!getClass().desiredAssertionStatus()) {
            throw new RuntimeException("Assertions must be enabled when running integration tests.");
        }

        GradleExecuter gradleExecuter = createExecuter(executerType);
        configureExecuter(gradleExecuter);
        try {
            gradleExecuter.assertCanExecute();
        } catch (AssertionError assertionError) {
            gradleExecuter = new ForkingGradleExecuter(getDistribution(), getTestDirectoryProvider());
            configureExecuter(gradleExecuter);
        }

        return gradleExecuter;
    }

    private void configureExecuter(GradleExecuter gradleExecuter) {
        copyTo(gradleExecuter);

        if (System.getProperty(UNKNOWN_OS_SYS_PROP) != null) {
            gradleExecuter.withGradleOpts("-Dos.arch=unknown architecture", "-Dos.name=unknown operating system", "-Dos.version=unknown version");
        }
    }

    private GradleExecuter createExecuter(Executer executerType) {
        switch (executerType) {
            case embedded:
                return new InProcessGradleExecuter(getDistribution(), getTestDirectoryProvider());
            case daemon:
                return new DaemonGradleExecuter(getDistribution(), getTestDirectoryProvider());
            case parallel:
                return new ParallelForkingGradleExecuter(getDistribution(), getTestDirectoryProvider());
            case forking:
                return new ForkingGradleExecuter(getDistribution(), getTestDirectoryProvider());
            default:
                throw new RuntimeException("Not a supported executer type: " + executerType);
        }
    }

}
