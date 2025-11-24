/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform;

import org.gradle.api.internal.tasks.testing.ClassTestDefinition;
import org.gradle.api.internal.tasks.testing.DirectoryBasedTestDefinition;
import org.gradle.api.internal.tasks.testing.TestDefinitionConsumer;
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.api.internal.tasks.testing.junit.AbstractJUnitTestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.junitplatform.filters.ClassMethodNameFilter;
import org.gradle.api.internal.tasks.testing.junitplatform.filters.DelegatingByTypeFilter;
import org.gradle.api.internal.tasks.testing.junitplatform.filters.FilePathFilter;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.DirectorySource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import javax.annotation.WillCloseWhenClosed;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.gradle.api.internal.tasks.testing.junit.JUnitTestExecutor.isNestedClassInsideEnclosedRunner;
import static org.junit.platform.launcher.EngineFilter.excludeEngines;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;

/**
 * A {@link TestDefinitionProcessor} for JUnit Platform.
 * <p>
 * This class is instantiated by reflection from {@link JUnitPlatformTestDefinitionProcessorFactory}.
 */
@SuppressWarnings("unused")
@NullMarked
public final class JUnitPlatformTestDefinitionProcessor extends AbstractJUnitTestDefinitionProcessor<TestDefinition> {
    private final JUnitPlatformSpec spec;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;

    @Nullable
    private CollectThenExecuteTestDefinitionConsumer testClassExecutor;
    @Nullable
    private BackwardsCompatibleLauncherSession launcherSession;
    @Nullable
    private ClassLoader junitClassLoader;

    public JUnitPlatformTestDefinitionProcessor(JUnitPlatformSpec spec, IdGenerator<?> idGenerator, ActorFactory actorFactory, Clock clock) {
        super(actorFactory);
        this.spec = spec;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public void assertTestFrameworkAvailable() {
        try {
            Class.forName("org.junit.platform.launcher.core.LauncherFactory");
        } catch (ClassNotFoundException e) {
            throw new TestFrameworkNotAvailableException("Failed to load JUnit Platform.  Please ensure that all JUnit Platform dependencies are available on the test's runtime classpath, including the JUnit Platform launcher.");
        }
    }

    @Override
    protected TestDefinitionConsumer<TestDefinition> createTestExecutor(Actor resultProcessorActor) {
        TestResultProcessor threadSafeResultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
        launcherSession = BackwardsCompatibleLauncherSession.open();
        ClassLoader junitClassLoader = Thread.currentThread().getContextClassLoader();
        testClassExecutor = new CollectThenExecuteTestDefinitionConsumer(threadSafeResultProcessor, launcherSession, junitClassLoader, spec, idGenerator, clock);
        return testClassExecutor;
    }

    @Override
    public void stop() {
        if (startedProcessing) {
            Objects.requireNonNull(testClassExecutor).processAllTestDefinitions();
            Objects.requireNonNull(launcherSession).close();
            super.stop();
        }
    }

    @NullMarked
    private static final class CollectThenExecuteTestDefinitionConsumer implements TestDefinitionConsumer<TestDefinition> {
        private final List<DiscoverySelector> selectors = new ArrayList<>();

        private final TestResultProcessor resultProcessor;
        private final BackwardsCompatibleLauncherSession launcherSession;
        private final ClassLoader junitClassLoader;
        private final JUnitPlatformSpec spec;
        private final IdGenerator<?> idGenerator;
        private final Clock clock;

        CollectThenExecuteTestDefinitionConsumer(TestResultProcessor resultProcessor, BackwardsCompatibleLauncherSession launcherSession, ClassLoader junitClassLoader, JUnitPlatformSpec spec, IdGenerator<?> idGenerator, Clock clock) {
            this.resultProcessor = resultProcessor;
            this.launcherSession = launcherSession;
            this.junitClassLoader = junitClassLoader;
            this.spec = spec;
            this.idGenerator = idGenerator;
            this.clock = clock;
        }

        @Override
        public void accept(TestDefinition testDefinition) {
            if (testDefinition instanceof ClassTestDefinition) {
                executeClass((ClassTestDefinition) testDefinition);
            } else if (testDefinition instanceof DirectoryBasedTestDefinition) {
                executeDirectory((DirectoryBasedTestDefinition) testDefinition);
            } else {
                throw new IllegalStateException("Unexpected test definition type " + testDefinition.getClass().getName());
            }
        }

        private void executeClass(ClassTestDefinition testDefinition) {
            Class<?> klass = loadClass(testDefinition.getTestClassName());
            if (isInnerClass(klass) || (supportsVintageTests() && isNestedClassInsideEnclosedRunner(klass))) {
                return;
            }
            selectors.add(DiscoverySelectors.selectClass(klass));
        }


        private void executeDirectory(DirectoryBasedTestDefinition testDefinition) {
            selectors.add(DiscoverySelectors.selectDirectory(testDefinition.getTestDefinitionsDir()));
        }

        private void processAllTestDefinitions() {
            LauncherDiscoveryRequest discoveryRequest = createLauncherDiscoveryRequest();
            TestExecutionListener executionListener = new JUnitPlatformTestExecutionListener(resultProcessor, clock, idGenerator, spec.getBaseDefinitionsDir());
            Launcher launcher = Objects.requireNonNull(launcherSession).getLauncher();
            if (spec.isDryRun()) {
                TestPlan testPlan = launcher.discover(discoveryRequest);
                executeDryRun(testPlan, executionListener);
            } else {
                launcher.execute(discoveryRequest, executionListener);
            }
        }

        private Class<?> loadClass(String className) {
            try {
                return Class.forName(className, false, junitClassLoader);
            } catch (ClassNotFoundException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        private boolean isInnerClass(Class<?> klass) {
            return klass.getEnclosingClass() != null && !Modifier.isStatic(klass.getModifiers());
        }

        /**
         * Test whether {@code org.junit.vintage:junit-vintage-engine} and {@code junit:junit} are
         * available on the classpath. This allows us to enable or disable certain behavior
         * which may attempt to load classes from these modules.
         */
        private boolean supportsVintageTests() {
            try {
                Class.forName("org.junit.vintage.engine.VintageTestEngine", false, junitClassLoader);
                Class.forName("org.junit.runner.Request", false, junitClassLoader);
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        private LauncherDiscoveryRequest createLauncherDiscoveryRequest() {
            LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors);

            addTestNameFilters(requestBuilder);
            addEnginesFilter(requestBuilder);
            addTagsFilter(requestBuilder);

            return requestBuilder.build();
        }

        private void addTestNameFilters(LauncherDiscoveryRequestBuilder requestBuilder) {
            TestFilterSpec filterSpec = spec.getFilter();
            if (isNotEmpty(filterSpec)) {
                TestSelectionMatcher matcher = new TestSelectionMatcher(filterSpec);

                DelegatingByTypeFilter delegatingFilter = new DelegatingByTypeFilter();

                if (matcher.hasClassBasedFilters()) {
                    ClassMethodNameFilter classFilter = new ClassMethodNameFilter(matcher);
                    delegatingFilter.addDelegate(ClassSource.class, classFilter);
                    delegatingFilter.addDelegate(MethodSource.class, classFilter);
                }
                if (matcher.hasPathBasedFilters()) {
                    FilePathFilter fileFilter = new FilePathFilter(matcher, spec.getBaseDefinitionsDir());
                    delegatingFilter.addDelegate(FileSource.class, fileFilter);
                    delegatingFilter.addDelegate(DirectorySource.class, fileFilter);
                }

                requestBuilder.filters(delegatingFilter);
            }
        }

        private void addEnginesFilter(LauncherDiscoveryRequestBuilder requestBuilder) {
            List<String> includeEngines = spec.getIncludeEngines();
            if (!includeEngines.isEmpty()) {
                requestBuilder.filters(includeEngines(includeEngines));
            }
            List<String> excludeEngines = spec.getExcludeEngines();
            if (!excludeEngines.isEmpty()) {
                requestBuilder.filters(excludeEngines(excludeEngines));
            }
        }

        private void addTagsFilter(LauncherDiscoveryRequestBuilder requestBuilder) {
            List<String> includeTags = spec.getIncludeTags();
            if (!includeTags.isEmpty()) {
                requestBuilder.filters(includeTags(includeTags));
            }
            List<String> excludeTags = spec.getExcludeTags();
            if (!excludeTags.isEmpty()) {
                requestBuilder.filters(excludeTags(excludeTags));
            }
        }

        private void executeDryRun(TestPlan testPlan, TestExecutionListener listener) {
            listener.testPlanExecutionStarted(testPlan);

            for (TestIdentifier root : testPlan.getRoots()) {
                dryRun(root, testPlan, listener);
            }

            listener.testPlanExecutionFinished(testPlan);
        }

        private void dryRun(TestIdentifier testIdentifier, TestPlan testPlan, TestExecutionListener listener) {
            if (testIdentifier.isTest()) {
                listener.executionSkipped(testIdentifier, "Gradle test execution dry run");
            } else {
                listener.executionStarted(testIdentifier);

                for (TestIdentifier child : testPlan.getChildren(testIdentifier)) {
                    dryRun(child, testPlan, listener);
                }
                listener.executionFinished(testIdentifier, TestExecutionResult.successful());
            }
        }

        private boolean isNotEmpty(TestFilterSpec filter) {
            return !filter.getIncludedTests().isEmpty()
                || !filter.getIncludedTestsCommandLine().isEmpty()
                || !filter.getExcludedTests().isEmpty();
        }
    }

    @NullMarked
    private static final class BackwardsCompatibleLauncherSession implements AutoCloseable {

        static BackwardsCompatibleLauncherSession open() {
            try {
                LauncherSession launcherSession = LauncherFactory.openSession();
                return new BackwardsCompatibleLauncherSession(launcherSession);
            } catch (NoSuchMethodError ignore) {
                // JUnit Platform version on test classpath does not yet support launcher sessions
                return new BackwardsCompatibleLauncherSession(LauncherFactory.create(), () -> {});
            }
        }

        private final Launcher launcher;
        private final Runnable onClose;

        BackwardsCompatibleLauncherSession(@WillCloseWhenClosed LauncherSession session) {
            this(session.getLauncher(), session::close);
        }

        BackwardsCompatibleLauncherSession(Launcher launcher, Runnable onClose) {
            this.launcher = launcher;
            this.onClose = onClose;
        }

        Launcher getLauncher() {
            return launcher;
        }

        @Override
        public void close() {
            onClose.run();
        }
    }
}
