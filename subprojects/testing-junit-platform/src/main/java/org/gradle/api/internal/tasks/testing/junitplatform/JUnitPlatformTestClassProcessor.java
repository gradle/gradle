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
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import static org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecutor.isNestedClassInsideEnclosedRunner;
import static org.junit.platform.launcher.EngineFilter.excludeEngines;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;

public class JUnitPlatformTestClassProcessor extends AbstractJUnitTestClassProcessor<JUnitPlatformSpec> {
    private static final Logger LOG = Logging.getLogger(JUnitPlatformTestClassProcessor.class);

    private CollectAllTestClassesExecutor testClassExecutor;

    public JUnitPlatformTestClassProcessor(JUnitPlatformSpec spec, IdGenerator<?> idGenerator, ActorFactory actorFactory, Clock clock) {
        super(spec, idGenerator, actorFactory, clock);
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
            Class<?> klass = loadClass(testClassName);
            if (isInnerClass(klass) || isNestedClassInsideEnclosedRunner(klass)) {
                return;
            }
            testClasses.add(klass);
        }

        private void processAllTestClasses() {
            Launcher launcher = LauncherFactory.create();
            launcher.registerTestExecutionListeners(new JUnitPlatformTestExecutionListener(resultProcessor, clock, idGenerator));
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
        if (!spec.getIncludedTests().isEmpty() || !spec.getIncludedTestsCommandLine().isEmpty() || !spec.getExcludedTests().isEmpty()) {
            TestSelectionMatcher matcher = new TestSelectionMatcher(spec.getIncludedTests(),
                spec.getExcludedTests(), spec.getIncludedTestsCommandLine());
            requestBuilder.filters(new SelectorNameFilter(matcher, loadSelectorFactories()));
        }
    }

    private Collection<TestSelectorFactory> loadSelectorFactories() {
        Set<TestSelectorFactory> selectorFactories = streamServiceProviders(new JoinClassLoader(
                getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader()),
            TestSelectorFactory.class)
            .collect(Collectors.toSet());
        LOG.info("Loaded test selector factories: {}", selectorFactories);
        return selectorFactories;
    }

    private Stream<TestSelectorFactory> streamServiceProviders(ClassLoader classLoader, Class<TestSelectorFactory> serviceProviderInterface) {
        return StreamSupport.stream(ServiceLoader.load(serviceProviderInterface, classLoader).spliterator(), false);
    }

    private static class SelectorNameFilter implements PostDiscoveryFilter {
        private final TestSelectionMatcher matcher;
        private final Collection<TestSelectorFactory> testSelectors;

        private SelectorNameFilter(TestSelectionMatcher matcher, Collection<TestSelectorFactory> testSelectors) {
            this.matcher = matcher;
            this.testSelectors = testSelectors;
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
            return descriptor.getSource()
                .flatMap(source -> or(
                    shouldRunExistingSelector(descriptor, source),
                    shouldRunClassSource(descriptor, checkingParent, source),
                    shouldRunParent(descriptor)
                )).orElse(true);
        }

        @SuppressWarnings("varargs")
        @SafeVarargs
        private static <T> Optional<T> or(Optional<T>... alternatives) {
            return Arrays.stream(alternatives)
                .filter(Optional::isPresent)
                .findFirst()
                .get();
        }

        private Optional<Boolean> shouldRunClassSource(TestDescriptor descriptor, boolean checkingParent, TestSource source) {
            return Optional.of(source)
                .filter(ClassSource.class::isInstance)
                .map(ClassSource.class::cast)
                .map(classSource -> shouldRunClassSource(descriptor, checkingParent, classSource));
        }

        private Optional<Boolean> shouldRunExistingSelector(TestDescriptor descriptor, TestSource source) {
            return findExistingSelector(source)
                .map(factory -> factory.createSelector(source))
                .map(selector -> shouldRunSelector(descriptor, selector));
        }

        private Optional<TestSelectorFactory> findExistingSelector(TestSource source) {
            Optional<TestSelectorFactory> maybeExistingSelector = testSelectors.stream()
                .filter(factory -> factory.supports(source))
                .findAny();
            if (LOG.isDebugEnabled()) {
                if (maybeExistingSelector.isPresent()) {
                    LOG.debug("Found selector {} for source {}", maybeExistingSelector.get().getClass().getSimpleName(), source);
                } else {
                    LOG.debug("No selector fround for source {}", source);
                }
            }
            return maybeExistingSelector;
        }

        private Optional<Boolean> shouldRunParent(TestDescriptor descriptor) {
            return descriptor.getParent().map(parent -> shouldRun(parent, true));
        }

        private boolean shouldRunSelector(TestDescriptor descriptor, TestSelectorFactory.TestSelector selector) {
            return matcher.matchesTest(selector.getContainerName(), selector.getSelectorName()) || matchesParentSelector(descriptor, selector.getSelectorName());
        }

        private boolean shouldRunClassSource(TestDescriptor descriptor, boolean checkingParent, ClassSource source) {
            if (!checkingParent) {
                for (TestDescriptor child : descriptor.getChildren()) {
                    if (shouldRun(child)) {
                        return true;
                    }
                }
            }
            if (descriptor.getChildren().isEmpty()) {
                String className = source.getClassName();
                return matcher.matchesTest(className, null)
                    || matcher.matchesTest(className, descriptor.getLegacyReportingName());
            } else {
                return true;
            }
        }

        private boolean matchesParentSelector(TestDescriptor descriptor, String methodName) {
            return descriptor.getParent().flatMap(this::className).filter(className -> matcher.matchesTest(className, methodName)).isPresent();
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
