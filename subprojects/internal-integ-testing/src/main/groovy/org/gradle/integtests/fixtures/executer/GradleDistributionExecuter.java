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
 * A JUnit rule which provides a {@link GradleExecuter} implementation that executes Gradle using a given {@link
 * GradleDistribution}. If not supplied in the constructor, this rule locates a field on the test object with type
 * {@link GradleDistribution}.
 *
 * By default, this executer will execute Gradle in a forked process. There is a system property which enables executing
 * Gradle in the current process.
 */
public class GradleDistributionExecuter extends AbstractDelegatingGradleExecuter {

    private static final String EXECUTER_SYS_PROP = "org.gradle.integtest.executer";
    private static final String UNKNOWN_OS_SYS_PROP = "org.gradle.integtest.unknownos";

    private BasicGradleDistribution dist;

    private boolean allowExtraLogging = true;

    private Executer executerType;

    public enum Executer {
        embedded(false),
        forking(true),
        daemon(true),
        embeddedDaemon(false),
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

    public static Executer getSystemPropertyExecuter() {
        return Executer.valueOf(System.getProperty(EXECUTER_SYS_PROP, Executer.forking.toString()));
    }

    public GradleDistributionExecuter(GradleDistribution dist, TestDirectoryProvider testWorkDirProvider) {
        this(getSystemPropertyExecuter(), dist, testWorkDirProvider);
    }

    private GradleDistributionExecuter(Executer executerType, GradleDistribution dist, TestDirectoryProvider testDirectoryProviders) {
        super(testDirectoryProviders);
        this.executerType = executerType;
        this.dist = dist;
    }

    /**
     * set true to allow the executer to increase the log level if necessary
     * to help out debugging. Set false to make the executer never update the log level.
     */
    public GradleDistributionExecuter setAllowExtraLogging(boolean allowExtraLogging) {
        this.allowExtraLogging = allowExtraLogging;
        return this;
    }

    public void setExecuterType(Executer executerType) {
        this.executerType = executerType;
    }

    public GradleDistributionExecuter withForkingExecuter() {
        if (!executerType.forks) {
            executerType = Executer.forking;
        }
        return this;
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
            gradleExecuter = new ForkingGradleExecuter(getTestDirectoryProvider(), dist.getGradleHomeDir());
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
            case embeddedDaemon:
                return new EmbeddedDaemonGradleExecuter(getTestDirectoryProvider());
            case embedded:
                return new InProcessGradleExecuter(getTestDirectoryProvider());
            case daemon:
                return new DaemonGradleExecuter(getTestDirectoryProvider(), dist.getGradleHomeDir(), !isQuiet() && allowExtraLogging, noDefaultJvmArgs);
            case parallel:
                return new ParallelForkingGradleExecuter(getTestDirectoryProvider(), dist.getGradleHomeDir());
            case forking:
                return new ForkingGradleExecuter(getTestDirectoryProvider(), dist.getGradleHomeDir());
            default:
                throw new RuntimeException("Not a supported executer type: " + executerType);
        }
    }

}
