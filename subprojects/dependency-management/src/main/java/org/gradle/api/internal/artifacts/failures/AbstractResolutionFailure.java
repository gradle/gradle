/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.failures;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.ResolutionFailure;
import org.gradle.internal.Cast;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AbstractResolutionFailure<T extends ComponentIdentifier> implements ResolutionFailure<T> {
    protected final T id;
    protected final Throwable problem;
    private final List<String> attemptedLocations;

    protected AbstractResolutionFailure(Throwable problem, T id) {
        this.problem = problem;
        this.id = id;
        this.attemptedLocations = extractAttemptedLocations(problem);
    }

    @Override
    public T getComponentId() {
        return id;
    }

    @Override
    public Throwable getProblem() {
        return problem;
    }

    @Nullable
    @Override
    public List<String> getAttemptedLocations() {
        return attemptedLocations;
    }

    public static <T extends ComponentIdentifier> ResolutionFailure<T> of(ComponentIdentifier id, Throwable err) {
        if (id instanceof ModuleComponentIdentifier) {
            return Cast.uncheckedCast(new DefaultModuleComponentResolutionFailure((ModuleComponentIdentifier) id, err));
        } else if (id instanceof ProjectComponentIdentifier) {
            return Cast.uncheckedCast(new DefaultProjectComponentResolutionFailure((ProjectComponentIdentifier) id, err));
        } else if (id instanceof ComponentArtifactIdentifier) {
            return Cast.uncheckedCast(new DefaultComponentArtifactResolutionFailure((ComponentArtifactIdentifier) id, err));
        } else if (id==null) {
            return Cast.uncheckedCast(new OpaqueComponentResolutionFailure(err));
        } else {
            throw new UnsupportedOperationException("Unsupported identifier type:" + id.getClass());
        }
    }

    private static ImmutableList<String> extractAttemptedLocations(Throwable problem) {
        if (problem instanceof ModuleVersionNotFoundException) {
            return ImmutableList.copyOf(((ModuleVersionNotFoundException) problem).getAttemptedLocations());
        }
        return null;
    }

}
