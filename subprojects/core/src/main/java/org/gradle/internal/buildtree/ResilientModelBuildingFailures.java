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

import java.util.List;

/**
 * Failures observed during resilient model building that must still fail the build, even though partial models are
 * returned to the client. These travel with the model result instead of via shared state, so an intermediate reader
 * can see from the types that a result may carry build failures. Two kinds are tracked:
 *
 * <ul>
 *     <li><em>Model builder failures</em> - thrown by a tooling model builder after the target project configured
 *     successfully. These are always propagated.</li>
 *     <li><em>Configuration failures</em> - captured while configuring projects leniently. When no tasks run these
 *     would otherwise be swallowed; they are propagated unless the build is already failing because of the same
 *     configuration failure (e.g. tasks were requested and configuration failed).</li>
 * </ul>
 */
@NullMarked
public final class ResilientModelBuildingFailures {

    private static final ResilientModelBuildingFailures EMPTY = new ResilientModelBuildingFailures(ImmutableList.of(), ImmutableList.of());

    private final List<Throwable> modelBuilderFailures;
    private final List<Throwable> configurationFailures;

    private ResilientModelBuildingFailures(List<Throwable> modelBuilderFailures, List<Throwable> configurationFailures) {
        this.modelBuilderFailures = modelBuilderFailures;
        this.configurationFailures = configurationFailures;
    }

    public static ResilientModelBuildingFailures empty() {
        return EMPTY;
    }

    public static ResilientModelBuildingFailures modelBuilderFailure(Throwable failure) {
        return new ResilientModelBuildingFailures(ImmutableList.of(failure), ImmutableList.of());
    }

    public static ResilientModelBuildingFailures configurationFailure(Throwable failure) {
        return new ResilientModelBuildingFailures(ImmutableList.of(), ImmutableList.of(failure));
    }

    public boolean isEmpty() {
        return modelBuilderFailures.isEmpty() && configurationFailures.isEmpty();
    }

    public List<Throwable> getModelBuilderFailures() {
        return modelBuilderFailures;
    }

    /**
     * Configuration failures, de-duplicated by identity. The same failure instance may be observed while building a
     * model for each project that failed to configure.
     */
    public List<Throwable> getConfigurationFailures() {
        return configurationFailures;
    }

    public ResilientModelBuildingFailures plus(ResilientModelBuildingFailures other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        ImmutableList.Builder<Throwable> configuration = ImmutableList.builder();
        configuration.addAll(configurationFailures);
        for (Throwable candidate : other.configurationFailures) {
            if (!containsIdentity(configurationFailures, candidate)) {
                configuration.add(candidate);
            }
        }
        return new ResilientModelBuildingFailures(
            ImmutableList.<Throwable>builder().addAll(modelBuilderFailures).addAll(other.modelBuilderFailures).build(),
            configuration.build()
        );
    }

    private static boolean containsIdentity(List<Throwable> failures, Throwable candidate) {
        for (Throwable existing : failures) {
            if (existing == candidate) {
                return true;
            }
        }
        return false;
    }
}
