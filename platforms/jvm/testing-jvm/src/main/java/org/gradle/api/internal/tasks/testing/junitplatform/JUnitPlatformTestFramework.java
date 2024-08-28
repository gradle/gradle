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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestFrameworkDistributionModule;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UsedByScanPlugin("test-retry")
public class JUnitPlatformTestFramework implements TestFramework {
    private static final Logger LOGGER = Logging.getLogger(JUnitPlatformTestFramework.class);

    private static final List<TestFrameworkDistributionModule> DISTRIBUTION_MODULES =
        ImmutableList.of(
            new TestFrameworkDistributionModule(
                "junit-platform-engine",
                Pattern.compile("junit-platform-engine-1.*\\.jar"),
                "org.junit.platform.engine.DiscoverySelector"
            ),
            new TestFrameworkDistributionModule(
                "junit-platform-launcher",
                Pattern.compile("junit-platform-launcher-1.*\\.jar"),
                "org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder"
            ),
            new TestFrameworkDistributionModule(
                "junit-platform-commons",
                Pattern.compile("junit-platform-commons-1.*\\.jar"),
                "org.junit.platform.commons.util.ReflectionUtils"
            )
        );

    private final JUnitPlatformOptions options;
    private final DefaultTestFilter filter;
    private final boolean useImplementationDependencies;
    private final Provider<Boolean> dryRun;
    private final ObjectFactory objects;

    public JUnitPlatformTestFramework(DefaultTestFilter filter, ObjectFactory objectFactory, boolean useImplementationDependencies, Provider<Boolean> dryRun) {
        this(filter, objectFactory, useImplementationDependencies, objectFactory.newInstance(JUnitPlatformOptions.class), dryRun);
    }

    private JUnitPlatformTestFramework(DefaultTestFilter filter, ObjectFactory objectFactory, boolean useImplementationDependencies, JUnitPlatformOptions options, Provider<Boolean> dryRun) {
        this.filter = filter;
        this.objects = objectFactory;
        this.useImplementationDependencies = useImplementationDependencies;
        this.options = options;
        this.dryRun = dryRun;
    }

    @UsedByScanPlugin("test-retry")
    @Override
    public TestFramework copyWithFilters(TestFilter newTestFilters) {
        JUnitPlatformOptions copiedOptions = objects.newInstance(JUnitPlatformOptions.class);
        copiedOptions.copyFrom(options);

        return new JUnitPlatformTestFramework(
            (DefaultTestFilter) newTestFilters,
            objects,
            useImplementationDependencies,
            copiedOptions,
            dryRun
        );
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        if (!JavaVersion.current().isJava8Compatible()) {
            throw new UnsupportedJavaRuntimeException("Running JUnit Platform requires Java 8+, please configure your test java executable with Java 8 or higher.");
        }
        validateOptions();
        return new JUnitPlatformTestClassProcessorFactory(new JUnitPlatformSpec(
            filter.toSpec(),
            // JUnitPlatformSpec get serialized to worker, so we create a copy of original sets,
            // to avoid serialization issues on worker for ImmutableSet that SetProperty returns
            new LinkedHashSet<>(options.getIncludeEngines().get()),
            new LinkedHashSet<>(options.getExcludeEngines().get()),
            new LinkedHashSet<>(options.getIncludeTags().get()),
            new LinkedHashSet<>(options.getExcludeTags().get()),
            dryRun.get()
        ));
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return workerProcessBuilder -> workerProcessBuilder.sharedPackages("org.junit");
    }

    @Override
    public List<TestFrameworkDistributionModule> getWorkerApplicationModulepathModules() {
        return DISTRIBUTION_MODULES;
    }

    @Override
    public boolean getUseDistributionDependencies() {
        return useImplementationDependencies;
    }

    @Override
    public JUnitPlatformOptions getOptions() {
        return options;
    }

    @Override
    public TestFrameworkDetector getDetector() {
        return null;
    }

    @Override
    public void close() throws IOException {
        // this test framework doesn't hold any state
    }

    private void validateOptions() {
        Set<String> intersection = Sets.newHashSet(options.getIncludeTags().get());
        intersection.retainAll(options.getExcludeTags().get());
        if (!intersection.isEmpty()) {
            if (intersection.size() == 1) {
                LOGGER.warn("The tag '" + intersection.iterator().next() + "' is both included and excluded.  " +
                    "This will result in the tag being excluded, which may not be what was intended.  " +
                    "Please either include or exclude the tag but not both.");
            } else {
                String allTags = intersection.stream().sorted().map(s -> "'" + s + "'").collect(Collectors.joining(", "));
                LOGGER.warn("The tags " + allTags + " are both included and excluded.  " +
                    "This will result in the tags being excluded, which may not be what was intended.  " +
                    "Please either include or exclude the tags but not both.");
            }
        }
    }
}
