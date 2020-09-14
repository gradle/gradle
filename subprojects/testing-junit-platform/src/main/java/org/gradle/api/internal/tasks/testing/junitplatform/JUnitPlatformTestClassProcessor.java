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
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.isNestedClassInsideEnclosedRunner;
import static org.junit.platform.launcher.EngineFilter.excludeEngines;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;

public class JUnitPlatformTestClassProcessor extends AbstractJUnitTestClassProcessor<JUnitPlatformSpec> {

    // see https://github.com/junit-team/junit5/pull/2264
    private static final BigDecimal INITIAL_VINTAGE_ENGINE_VERSION_WITH_SPOCK_METHOD_SELECTOR_SUPPORT = new BigDecimal("5.7");

    private final TestSelectionMatcher testSelectionMatcher;
    private final Function<Class<?>, Stream<DiscoverySelector>> selectorProvider;

    private CollectAllTestClassesExecutor testClassExecutor;

    public JUnitPlatformTestClassProcessor(JUnitPlatformSpec spec, IdGenerator<?> idGenerator, ActorFactory actorFactory, Clock clock) {
        super(spec, idGenerator, actorFactory, clock);
        testSelectionMatcher = createTestSelectionMatcher(spec);
        selectorProvider = createSelectorProvider(testSelectionMatcher);
    }

    private static TestSelectionMatcher createTestSelectionMatcher(JUnitPlatformSpec spec) {
        if (!spec.getIncludedTests().isEmpty() || !spec.getIncludedTestsCommandLine().isEmpty() || !spec.getExcludedTests().isEmpty()) {
            return new TestSelectionMatcher(spec.getIncludedTests(), spec.getExcludedTests(), spec.getIncludedTestsCommandLine());
        }
        return null;
    }

    private static Function<Class<?>, Stream<DiscoverySelector>> createSelectorProvider(TestSelectionMatcher testSelectionMatcher) {
        if (testSelectionMatcher != null && vintageEngineSupportsMethodSelectorsForSpockMethods()) {
            return testClass -> {
                Set<String> selectedMethods = testSelectionMatcher.determineSelectedMethods(testClass.getName());
                if (!selectedMethods.isEmpty()) {
                    return selectedMethods.stream()
                        .map(methodName -> DiscoverySelectors.selectMethod(testClass, methodName));
                }
                return Stream.of(DiscoverySelectors.selectClass(testClass));
            };
        }
        return testClass -> Stream.of(DiscoverySelectors.selectClass(testClass));
    }

    @Override
    protected TestResultProcessor createResultProcessorChain(TestResultProcessor resultProcessor) {
        return resultProcessor;
    }

    @Override
    protected Action<String> createTestExecutor(Actor resultProcessorActor) {
        TestResultProcessor threadSafeResultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
        testClassExecutor = new CollectAllTestClassesExecutor(threadSafeResultProcessor);
        return testClassExecutor;
    }

    @Override
    public void stop() {
        testClassExecutor.processAllTestClasses();
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
            Class<?> testClass;
            try {
                testClass = loadApplicationClass(testClassName);
            } catch (ClassNotFoundException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            if (isInnerClass(testClass) || isNestedClassInsideEnclosedRunner(testClass)) {
                return;
            }
            testClasses.add(testClass);
        }

        private void processAllTestClasses() {
            Launcher launcher = LauncherFactory.create();
            launcher.registerTestExecutionListeners(new JUnitPlatformTestExecutionListener(resultProcessor, clock, idGenerator));
            launcher.execute(createLauncherDiscoveryRequest(testClasses));
        }
    }

    private boolean isInnerClass(Class<?> testClass) {
        return testClass.getEnclosingClass() != null && !Modifier.isStatic(testClass.getModifiers());
    }

    private LauncherDiscoveryRequest createLauncherDiscoveryRequest(List<Class<?>> testClasses) {
        List<DiscoverySelector> selectors = testClasses.stream()
            .flatMap(selectorProvider)
            .collect(toList());

        LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request().selectors(selectors);

        addTestNameFilter(requestBuilder);
        addEngineFilters(requestBuilder);
        addTagFilters(requestBuilder);

        return requestBuilder.build();
    }

    private void addEngineFilters(LauncherDiscoveryRequestBuilder requestBuilder) {
        if (!spec.getIncludeEngines().isEmpty()) {
            requestBuilder.filters(includeEngines(spec.getIncludeEngines()));
        }
        if (!spec.getExcludeEngines().isEmpty()) {
            requestBuilder.filters(excludeEngines(spec.getExcludeEngines()));
        }
    }

    private void addTagFilters(LauncherDiscoveryRequestBuilder requestBuilder) {
        if (!spec.getIncludeTags().isEmpty()) {
            requestBuilder.filters(includeTags(spec.getIncludeTags()));
        }
        if (!spec.getExcludeTags().isEmpty()) {
            requestBuilder.filters(excludeTags(spec.getExcludeTags()));
        }
    }

    private void addTestNameFilter(LauncherDiscoveryRequestBuilder requestBuilder) {
        if (testSelectionMatcher != null) {
            requestBuilder.filters(new ClassMethodNameFilter(testSelectionMatcher));
        }
    }

    private static boolean vintageEngineSupportsMethodSelectorsForSpockMethods() {
        Optional<TestEngine> vintageTestEngine = instantiateVintageTestEngine();
        return vintageTestEngine
            .flatMap(JUnitPlatformTestClassProcessor::parseTestEngineMajorMinorVersion)
            .filter(version -> version.compareTo(INITIAL_VINTAGE_ENGINE_VERSION_WITH_SPOCK_METHOD_SELECTOR_SUPPORT) >= 0)
            .isPresent();
    }

    private static Optional<TestEngine> instantiateVintageTestEngine() {
        try {
            Class<?> result = loadApplicationClass("org.junit.vintage.engine.VintageTestEngine");
            return Optional.of((TestEngine) result.getConstructor().newInstance());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> parseTestEngineMajorMinorVersion(TestEngine testEngine) {
        return testEngine.getVersion()
            .map(version -> Pattern.compile("^(\\d+\\.\\d+).*$").matcher(version))
            .filter(Matcher::matches)
            .map(matcher -> new BigDecimal(matcher.group(1)));
    }

    private static Class<?> loadApplicationClass(String className) throws ClassNotFoundException {
        ClassLoader applicationClassloader = Thread.currentThread().getContextClassLoader();
        return Class.forName(className, false, applicationClassloader);
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
                if (descriptor.getChildren().isEmpty()) {
                    String className = ((ClassSource) source.get()).getClassName();
                    return matcher.mayIncludeClass(className)
                        || matcher.matchesTest(className, descriptor.getLegacyReportingName());
                }
            }

            return false;
        }

        private boolean classMatch(TestDescriptor descriptor) {
            while (descriptor.getParent().isPresent()) {
                if (className(descriptor).filter(className -> matcher.matchesTest(className, null)).isPresent()) {
                    return true;
                }
                descriptor = descriptor.getParent().get();
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

}
