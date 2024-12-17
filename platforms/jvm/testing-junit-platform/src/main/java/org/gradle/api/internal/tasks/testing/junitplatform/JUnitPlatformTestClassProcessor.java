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

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.api.internal.tasks.testing.junit.AbstractJUnitTestClassProcessor;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import javax.annotation.Nonnull;
import javax.annotation.WillCloseWhenClosed;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.isNestedClassInsideEnclosedRunner;
import static org.junit.platform.launcher.EngineFilter.excludeEngines;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;

public class JUnitPlatformTestClassProcessor extends AbstractJUnitTestClassProcessor {
    private final JUnitPlatformSpec spec;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;

    private CollectAllTestClassesExecutor testClassExecutor;
    private BackwardsCompatibleLauncherSession launcherSession;
    private ClassLoader junitClassLoader;

    public JUnitPlatformTestClassProcessor(JUnitPlatformSpec spec, IdGenerator<?> idGenerator, ActorFactory actorFactory, Clock clock) {
        super(actorFactory);
        this.spec = spec;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    protected TestResultProcessor createResultProcessorChain(TestResultProcessor resultProcessor) {
        return resultProcessor;
    }

    @Override
    protected Action<String> createTestExecutor(Actor resultProcessorActor) {
        TestResultProcessor threadSafeResultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
        launcherSession = BackwardsCompatibleLauncherSession.open();
        junitClassLoader = Thread.currentThread().getContextClassLoader();
        testClassExecutor = new CollectAllTestClassesExecutor(threadSafeResultProcessor);
        return testClassExecutor;
    }

    @Override
    public void stop() {
        testClassExecutor.processAllTestClasses();
        launcherSession.close();
        super.stop();
    }

    private class CollectAllTestClassesExecutor implements Action<String> {
        private final List<Class<?>> testClasses = new ArrayList<>();
        private final TestResultProcessor resultProcessor;

        CollectAllTestClassesExecutor(TestResultProcessor resultProcessor) {
            this.resultProcessor = resultProcessor;
        }

        @Override
        public void execute(@Nonnull String testClassName) {
            Class<?> klass = loadClass(testClassName);
            if (isInnerClass(klass) || (supportsVintageTests() && isNestedClassInsideEnclosedRunner(klass))) {
                return;
            }
            testClasses.add(klass);
        }

        private void processAllTestClasses() {
            LauncherDiscoveryRequest discoveryRequest = createLauncherDiscoveryRequest(testClasses);
            TestExecutionListener executionListener = new JUnitPlatformTestExecutionListener(resultProcessor, clock, idGenerator);
            Launcher launcher = launcherSession.getLauncher();
            if (spec.isDryRun()) {
                TestPlan testPlan = launcher.discover(discoveryRequest);
                executeDryRun(testPlan, executionListener);
            } else {
                launcher.execute(discoveryRequest, executionListener);
            }
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

    private boolean isInnerClass(Class<?> klass) {
        return klass.getEnclosingClass() != null && !Modifier.isStatic(klass.getModifiers());
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, junitClassLoader);
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private LauncherDiscoveryRequest createLauncherDiscoveryRequest(List<Class<?>> testClasses) {
        List<DiscoverySelector> discoverySelectors = null;
        if (spec.getSelectorPatterns().isEmpty()) {
            discoverySelectors =testClasses.stream()
                .map(DiscoverySelectors::selectClass)
                .collect(Collectors.toList());
        } else {
            discoverySelectors = JUnitPlatformSelectorParser.parse(spec.getSelectorPatterns());
        }

        LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request().selectors(discoverySelectors);

        addTestNameFilters(requestBuilder);
        addEnginesFilter(requestBuilder);
        addTagsFilter(requestBuilder);

        return requestBuilder.build();
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

    private void addTestNameFilters(LauncherDiscoveryRequestBuilder requestBuilder) {
        TestFilterSpec filter = spec.getFilter();
        if (isNotEmpty(filter)) {
            TestSelectionMatcher matcher = new TestSelectionMatcher(filter);
            requestBuilder.filters(new ClassMethodNameFilter(matcher));
        }
    }

    private static boolean isNotEmpty(TestFilterSpec filter) {
        return !filter.getIncludedTests().isEmpty()
            || !filter.getIncludedTestsCommandLine().isEmpty()
            || !filter.getExcludedTests().isEmpty();
    }

    private static class ClassMethodNameFilter implements PostDiscoveryFilter {
        private final TestSelectionMatcher matcher;

        private ClassMethodNameFilter(TestSelectionMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public FilterResult apply(TestDescriptor descriptor) {
            if (classMatch(descriptor)) {
                return FilterResult.included("Class match");
            }
            return FilterResult.includedIf(shouldRun(descriptor), () -> "Method or class match", () -> "Method or class mismatch");
        }

        private boolean shouldRun(TestDescriptor descriptor) {
            return shouldRun(descriptor, false);
        }

        private boolean shouldRun(TestDescriptor descriptor, boolean checkingParent) {
            Optional<TestSource> source = descriptor.getSource();
            if (!source.isPresent()) {
                return true;
            }

            TestSource testSource = source.get();
            if (testSource instanceof MethodSource) {
                return shouldRun(descriptor, (MethodSource) testSource);
            }

            if (testSource instanceof ClassSource) {
                return shouldRun(descriptor, checkingParent, (ClassSource) testSource);
            }

            Optional<TestDescriptor> parent = descriptor.getParent();
            return parent.isPresent() && shouldRun(parent.get(), true);
        }

        private boolean shouldRun(TestDescriptor descriptor, boolean checkingParent, ClassSource classSource) {
            Set<? extends TestDescriptor> children = descriptor.getChildren();
            if (!checkingParent) {
                for (TestDescriptor child : children) {
                    if (shouldRun(child)) {
                        return true;
                    }
                }
            }
            if (children.isEmpty()) {
                String className = classSource.getClassName();
                return matcher.matchesTest(className, null)
                    || matcher.matchesTest(className, descriptor.getLegacyReportingName());
            }
            return true;
        }

        private boolean shouldRun(TestDescriptor descriptor, MethodSource methodSource) {
            String methodName = methodSource.getMethodName();
            return matcher.matchesTest(methodSource.getClassName(), methodName)
                || matchesParentMethod(descriptor, methodName);
        }

        private boolean matchesParentMethod(TestDescriptor descriptor, String methodName) {
            return descriptor.getParent()
                .flatMap(this::className)
                .filter(className -> matcher.matchesTest(className, methodName))
                .isPresent();
        }

        private boolean classMatch(TestDescriptor descriptor) {
            while (true) {
                Optional<TestDescriptor> parent = descriptor.getParent();
                if (!parent.isPresent()) {
                    break;
                }
                if (className(descriptor).filter(className -> matcher.matchesTest(className, null)).isPresent()) {
                    return true;
                }
                descriptor = parent.get();
            }
            return false;
        }

        private Optional<String> className(TestDescriptor descriptor) {
            return descriptor.getSource()
                .filter(ClassSource.class::isInstance)
                .map(ClassSource.class::cast)
                .map(ClassSource::getClassName);
        }
    }

    private static class BackwardsCompatibleLauncherSession implements AutoCloseable {

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
