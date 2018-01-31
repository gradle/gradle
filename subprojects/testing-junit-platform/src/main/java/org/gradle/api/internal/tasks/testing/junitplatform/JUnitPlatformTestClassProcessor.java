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
import org.gradle.api.internal.tasks.testing.junit.TestClassExecutionListener;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.Optional;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.EngineFilter.excludeEngines;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;

public class JUnitPlatformTestClassProcessor extends AbstractJUnitTestClassProcessor<JUnitPlatformSpec> {
    public JUnitPlatformTestClassProcessor(JUnitPlatformSpec spec, IdGenerator<?> idGenerator, ActorFactory actorFactory, Clock clock) {
        super(spec, idGenerator, actorFactory, clock);
    }

    @Override
    protected Action<String> createTestExecutor(TestResultProcessor threadSafeResultProcessor, TestClassExecutionListener threadSafeTestClassListener) {
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(new JUnitPlatformTestExecutionListener(threadSafeResultProcessor, clock, idGenerator));
        return testClassName -> {
            threadSafeTestClassListener.testClassStarted(testClassName);
            Throwable e = null;
            try {
                launcher.execute(createLauncherDiscoveryRequest(testClassName));
            } catch (Throwable throwable) {
                e = throwable;
            }
            threadSafeTestClassListener.testClassFinished(e);
        };
    }

    private LauncherDiscoveryRequest createLauncherDiscoveryRequest(String testClassName) {
        LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(testClassName));

        addTestsFilter(requestBuilder);
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

    private void addTestsFilter(LauncherDiscoveryRequestBuilder requestBuilder) {
        if (!spec.getIncludedTests().isEmpty() || !spec.getIncludedTestsCommandLine().isEmpty()) {
            TestSelectionMatcher matcher = new TestSelectionMatcher(spec.getIncludedTests(), spec.getIncludedTestsCommandLine());
            requestBuilder.filters(new MethodNameFilter(matcher));
        }
    }

    private static class MethodNameFilter implements PostDiscoveryFilter {
        private final TestSelectionMatcher matcher;

        public MethodNameFilter(TestSelectionMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public FilterResult apply(TestDescriptor descriptor) {
            if (shouldInclude(descriptor)) {
                return FilterResult.included("included");
            } else {
                return FilterResult.excluded("excluded");
            }
        }

        private boolean shouldInclude(TestDescriptor descriptor) {
            Optional<TestSource> source = descriptor.getSource();
            if (!source.isPresent()) {
                return true;
            }

            if (source.get() instanceof MethodSource) {
                MethodSource methodSource = (MethodSource) source.get();
                return matcher.matchesTest(methodSource.getClassName(), methodSource.getMethodName());
            } else if (source.get() instanceof ClassSource) {
                for (TestDescriptor child : descriptor.getChildren()) {
                    if (shouldInclude(child)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

}
