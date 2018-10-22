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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.api.internal.tasks.testing.junit.AbstractJUnitTestClassProcessor;
import org.gradle.api.internal.tasks.testing.junit.TestClassExecutionListener;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import static org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.isNestedClassInsideEnclosedRunner;
import static org.gradle.api.internal.tasks.testing.junitplatform.VintageTestNameAdapter.isVintageDynamicLeafTest;
import static org.gradle.api.internal.tasks.testing.junitplatform.VintageTestNameAdapter.vintageDynamicClassName;
import static org.gradle.api.internal.tasks.testing.junitplatform.VintageTestNameAdapter.vintageDynamicMethodName;
import static org.junit.platform.launcher.EngineFilter.excludeEngines;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;

public class JUnitPlatformTestClassProcessor extends AbstractJUnitTestClassProcessor<JUnitPlatformSpec> {
    private TestResultProcessor resultProcessor;
    private TestClassExecutionListener executionListener;
    private CollectAllTestClassesExecutor testClassExecutor;

    public JUnitPlatformTestClassProcessor(JUnitPlatformSpec spec, IdGenerator<?> idGenerator, ActorFactory actorFactory, Clock clock) {
        super(spec, idGenerator, actorFactory, clock);
    }

    @Override
    protected Action<String> createTestExecutor(TestResultProcessor threadSafeResultProcessor, TestClassExecutionListener threadSafeTestClassListener) {
        resultProcessor = threadSafeResultProcessor;
        executionListener = threadSafeTestClassListener;
        testClassExecutor = new CollectAllTestClassesExecutor();
        return testClassExecutor;
    }

    @Override
    public void stop() {
        testClassExecutor.processAllTestClasses();
        super.stop();
    }

    private class CollectAllTestClassesExecutor implements Action<String> {
        private final List<Class<?>> testClasses = new ArrayList<>();

        @Override
        public void execute(String testClassName) {
            Class<?> klass = loadClass(testClassName);
            if (isInnerClass(klass) || isNestedClassInsideEnclosedRunner(klass)) {
                return;
            }
            testClasses.add(klass);
        }

        private void processAllTestClasses() {
            Launcher launcher = LauncherFactory.create();
            launcher.registerTestExecutionListeners(new JUnitPlatformTestExecutionListener(resultProcessor, clock, idGenerator, executionListener));
            launcher.execute(createLauncherDiscoveryRequest(testClasses));
        }
    }

    private boolean isInnerClass(Class<?> klass) {
        return klass.getEnclosingClass() != null && !Modifier.isStatic(klass.getModifiers());
    }

    private Class<?> loadClass(String className) {
        try {
            ClassLoader applicationClassloader = Thread.currentThread().getContextClassLoader();
            return Class.forName(className, false, applicationClassloader);
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private LauncherDiscoveryRequest createLauncherDiscoveryRequest(List<Class<?>> testClasses) {
        List<DiscoverySelector> classSelectors = testClasses.stream()
            .map(DiscoverySelectors::selectClass)
            .collect(Collectors.toList());

        LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request().selectors(classSelectors);

        addTestNameFilters(requestBuilder);
        addEnginesFilter(requestBuilder);
        addTagsFilter(requestBuilder);

        return requestBuilder.build();
    }

    private void addEnginesFilter(LauncherDiscoveryRequestBuilder requestBuilder) {
        if (!spec.getIncludeEngines().isEmpty()) {
            requestBuilder.filters(includeEngines(spec.getIncludeEngines()));
        }
        if (!spec.getExcludeEngines().isEmpty()) {
            requestBuilder.filters(excludeEngines(spec.getExcludeEngines()));
        }
    }

    private void addTagsFilter(LauncherDiscoveryRequestBuilder requestBuilder) {
        if (!spec.getIncludeTags().isEmpty()) {
            requestBuilder.filters(includeTags(spec.getIncludeTags()));
        }
        if (!spec.getExcludeTags().isEmpty()) {
            requestBuilder.filters(excludeTags(spec.getExcludeTags()));
        }
    }

    private void addTestNameFilters(LauncherDiscoveryRequestBuilder requestBuilder) {
        if (!spec.getIncludedTests().isEmpty() || !spec.getIncludedTestsCommandLine().isEmpty()) {
            TestSelectionMatcher matcher = new TestSelectionMatcher(spec.getIncludedTests(),
                spec.getExcludedTests(), spec.getIncludedTestsCommandLine());
            requestBuilder.filters(new ClassMethodNameFilter(matcher));
        }
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
            if (shouldRun(descriptor)) {
                return FilterResult.included("Method or class match");
            } else {
                return FilterResult.excluded("Method or class mismatch");
            }
        }

        private boolean shouldRun(TestDescriptor descriptor) {
            Optional<TestSource> source = descriptor.getSource();
            if (!source.isPresent()) {
                return true;
            }

            if (source.get() instanceof MethodSource) {
                MethodSource methodSource = (MethodSource) source.get();
                return matcher.matchesTest(methodSource.getClassName(), methodSource.getMethodName());
            } else if (source.get() instanceof ClassSource) {
                for (TestDescriptor child : descriptor.getChildren()) {
                    if (shouldRun(child)) {
                        return true;
                    }
                }
                if (isVintageDynamicLeafTest(descriptor, source.get())) {
                    return shouldRunVintageDynamicTest(descriptor);
                }
            }

            return false;
        }

        private boolean classMatch(TestDescriptor descriptor) {
            while (descriptor.getParent().isPresent()) {
                if (isClass(descriptor) && matcher.matchesTest(className(descriptor), null)) {
                    return true;
                }
                descriptor = descriptor.getParent().get();
            }
            return false;
        }

        private boolean isClass(TestDescriptor descriptor) {
            return descriptor.getSource().isPresent() && descriptor.getSource().get() instanceof ClassSource;
        }

        private String className(TestDescriptor descriptor) {
            return ClassSource.class.cast(descriptor.getSource().get()).getClassName();
        }

        private boolean shouldRunVintageDynamicTest(TestDescriptor descriptor) {
            return matcher.matchesTest(vintageDynamicClassName(descriptor.getUniqueId()), vintageDynamicMethodName(descriptor.getUniqueId()));
        }
    }

}
