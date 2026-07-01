/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.buildtree;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects failures that resilient model building did not throw straight away, so that partial models could still be
 * returned to the client. They are reported here as models are built and raised when the build finishes, so the build
 * still fails. The two kinds are kept apart because they are reported differently at finish (see
 * {@code DefaultBuildTreeLifecycleController}).
 * <p>
 * Thread-safe: models may be queried in parallel.
 */
@NullMarked
public final class ResilientFailureCollector {

    private final List<Throwable> modelBuilderFailures = new ArrayList<>();
    private final List<Throwable> configurationFailures = new ArrayList<>();

    /**
     * A tooling model builder threw after its target project had configured successfully.
     */
    public synchronized void addModelBuilderFailure(Throwable failure) {
        modelBuilderFailures.add(failure);
    }

    /**
     * A project or the build itself failed to configure.
     */
    public synchronized void addConfigurationFailure(Throwable failure) {
        configurationFailures.add(failure);
    }

    public synchronized List<Throwable> getModelBuilderFailures() {
        return ImmutableList.copyOf(modelBuilderFailures);
    }

    public synchronized List<Throwable> getConfigurationFailures() {
        return ImmutableList.copyOf(configurationFailures);
    }
}
