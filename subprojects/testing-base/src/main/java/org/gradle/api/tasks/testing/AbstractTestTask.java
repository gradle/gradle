/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;
import org.gradle.api.internal.tasks.testing.logging.DefaultTestLoggingContainer;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;

/**
 * @since 4.4
 */
public abstract class AbstractTestTask extends ConventionTask {
    protected final ListenerBroadcast<TestListener> testListenerBroadcaster;
    protected final ListenerBroadcast<TestOutputListener> testOutputListenerBroadcaster;
    protected final ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster;
    protected final TestLoggingContainer testLogging;
    private File binResultsDir;
    private boolean ignoreFailures;

    /**
     *
     */
    public AbstractTestTask() {
        Instantiator instantiator = getInstantiator();
        testLogging = instantiator.newInstance(DefaultTestLoggingContainer.class, instantiator);
        ListenerManager listenerManager = getListenerManager();
        testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);
        testOutputListenerBroadcaster = listenerManager.createAnonymousBroadcaster(TestOutputListener.class);
        testListenerBroadcaster = listenerManager.createAnonymousBroadcaster(TestListener.class);
    }

    protected abstract TestExecutionSpec createTestExecutionSpec();

    // only way I know of to determine current log level
    protected LogLevel determineCurrentLogLevel() {
        for (LogLevel level : LogLevel.values()) {
            if (getLogger().isEnabled(level)) {
                return level;
            }
        }
        throw new AssertionError("could not determine current log level");
    }

    @Inject
    protected ProgressLoggerFactory getProgressLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the root folder for the test results in internal binary format.
     *
     * @return the test result directory, containing the test results in binary format.
     */
    @OutputDirectory
    @Incubating
    public File getBinResultsDir() {
        return binResultsDir;
    }

    /**
     * Sets the root folder for the test results in internal binary format.
     *
     * @param binResultsDir The root folder
     */
    @Incubating
    public void setBinResultsDir(File binResultsDir) {
        this.binResultsDir = binResultsDir;
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Registers a test listener with this task. Consider also the following handy methods for quicker hooking into test execution: {@link #beforeTest(groovy.lang.Closure)}, {@link
     * #afterTest(groovy.lang.Closure)}, {@link #beforeSuite(groovy.lang.Closure)}, {@link #afterSuite(groovy.lang.Closure)} <p> This listener will NOT be notified of tests executed by other tasks. To
     * get that behavior, use {@link org.gradle.api.invocation.Gradle#addListener(Object)}.
     *
     * @param listener The listener to add.
     */
    public void addTestListener(TestListener listener) {
        testListenerBroadcaster.add(listener);
    }

    @Inject
    protected ListenerManager getListenerManager() {
        throw new UnsupportedOperationException();
    }

    /**
     * Registers a output listener with this task. Quicker way of hooking into output events is using the {@link #onOutput(groovy.lang.Closure)} method.
     *
     * @param listener The listener to add.
     */
    public void addTestOutputListener(TestOutputListener listener) {
        testOutputListenerBroadcaster.add(listener);
    }

    /**
     * Unregisters a test listener with this task.  This method will only remove listeners that were added by calling {@link #addTestListener(TestListener)} on this task.
     * If the listener was registered with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything. Instead, use {@link
     * org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestListener(TestListener listener) {
        testListenerBroadcaster.remove(listener);
    }

    /**
     * Unregisters a test output listener with this task.  This method will only remove listeners that were added by calling {@link #addTestOutputListener(TestOutputListener)}
     * on this task.  If the listener was registered with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything. Instead, use {@link
     * org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestOutputListener(TestOutputListener listener) {
        testOutputListenerBroadcaster.remove(listener);
    }

    @Internal
    protected ListenerBroadcast<TestListenerInternal> getTestListenerInternalBroadcaster() {
        return testListenerInternalBroadcaster;
    }

    @Inject
    protected BuildOperationExecutor getBuildOperationExecutor() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * {@inheritDoc}
     */
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    @Inject
    protected Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }
}
