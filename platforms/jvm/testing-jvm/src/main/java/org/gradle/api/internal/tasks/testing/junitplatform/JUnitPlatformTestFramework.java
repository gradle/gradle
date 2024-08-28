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

import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestDefinitionProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import javax.inject.Inject;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@UsedByScanPlugin("test-retry")
public abstract class JUnitPlatformTestFramework implements TestFramework {
    private static final Logger LOGGER = Logging.getLogger(JUnitPlatformTestFramework.class);

    private final DefaultTestFilter filter;
    private final Provider<Boolean> dryRun;
    private final ProjectLayout projectLayout;

    @Inject
    public JUnitPlatformTestFramework(DefaultTestFilter filter, Provider<Boolean> dryRun, ProjectLayout projectLayout) {
        this.filter = filter;
        this.dryRun = dryRun;
        this.projectLayout = projectLayout;
    }

    @UsedByScanPlugin("test-retry")
    @Override
    public TestFramework copyWithFilters(TestFilter newTestFilters) {
        JUnitPlatformTestFramework newTestFramework = getObjectFactory().newInstance(JUnitPlatformTestFramework.class,
            newTestFilters,
            dryRun,
            projectLayout);

        newTestFramework.getOptions().copyFrom(getOptions());
        return newTestFramework;
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Override
    public WorkerTestDefinitionProcessorFactory<?> getProcessorFactory() {
        validateOptions();
        return new JUnitPlatformTestDefinitionProcessorFactory(new JUnitPlatformSpec(
            filter.toSpec(),
            // JUnitPlatformSpec get serialized to worker, so we create a copy of original sets,
            // to avoid serialization issues on worker for ImmutableSet that SetProperty returns
            new LinkedHashSet<>(getOptions().getIncludeEngines().get()),
            new LinkedHashSet<>(getOptions().getExcludeEngines().get()),
            new LinkedHashSet<>(getOptions().getIncludeTags().get()),
            new LinkedHashSet<>(getOptions().getExcludeTags().get()),
            dryRun.get(), projectLayout.getProjectDirectory().getAsFile()
        ));
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return workerProcessBuilder -> workerProcessBuilder.sharedPackages("org.junit");
    }

    @Override
    @Nested
    public abstract JUnitPlatformOptions getOptions();

    @Override
    public TestFrameworkDetector getDetector() {
        return null;
    }

    @Override
    public void close() throws IOException {
        // this test framework doesn't hold any state
    }

    private void validateOptions() {
        Set<String> intersection = Sets.newHashSet(getOptions().getIncludeTags().get());
        intersection.retainAll(getOptions().getExcludeTags().get());
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

    @Override
    public boolean supportsNonClassBasedTesting() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "JUnit Platform";
    }
}
