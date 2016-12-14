/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.cleanup;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.List;

public class BuildOutputCleanupListener implements ModelConfigurationListener {

    private static final Logger LOGGER = Logging.getLogger(DefaultBuildOutputCleanupRegistry.class);
    private final BuildOutputCleanupRegistry buildOutputCleanupRegistry;

    public BuildOutputCleanupListener(BuildOutputCleanupRegistry buildOutputCleanupRegistry) {
        this.buildOutputCleanupRegistry = buildOutputCleanupRegistry;
    }

    @Override
    public void onConfigure(GradleInternal model) {
        if (requiresCleanup()) {
            for (File output : buildOutputCleanupRegistry.getOutputs()) {
                deleteOutput(output);
            }
        }
    }

    private boolean requiresCleanup() {
        List<BuildOutputCleanupStrategy> strategiesToBeCleanedUp = CollectionUtils.filter(buildOutputCleanupRegistry.getStrategies(), new Spec<BuildOutputCleanupStrategy>() {
            @Override
            public boolean isSatisfiedBy(BuildOutputCleanupStrategy strategy) {
                return strategy.requiresCleanup();
            }
        });

        return !strategiesToBeCleanedUp.isEmpty();
    }

    private void deleteOutput(File output) {
        try {
            if (output.isDirectory() && output.list().length > 0) {
                GFileUtils.cleanDirectory(output);
                LOGGER.quiet(String.format("Cleaned up directory '%s'", output));
            } else if (output.isFile()) {
                GFileUtils.forceDelete(output);
                LOGGER.quiet(String.format("Cleaned up file '%s'", output));
            }
        } catch (UncheckedIOException e) {
            LOGGER.warn(String.format("Unable to clean up '%s'", output), e);
        }
    }
}
