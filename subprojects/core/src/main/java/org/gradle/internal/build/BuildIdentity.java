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

package org.gradle.internal.build;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

/**
 * Identifies a single build within the build tree.
 * <p>
 * This is the immutable identity of a build, as opposed to {@link BuildState}, which also
 * owns the build's mutable model and service scope. Prefer this type over {@code BuildState}
 * wherever only the identity of a build is required.
 */
@ServiceScope(Scope.Build.class)
public final class BuildIdentity implements DisplayName {

    private final Path buildPath;
    private final BuildIdentifier buildIdentifier;
    private final DisplayName displayName;

    public BuildIdentity(Path buildPath) {
        if (!buildPath.isAbsolute()) {
            throw new IllegalArgumentException("Build path must be absolute: " + buildPath);
        }

        this.buildPath = buildPath;
        this.buildIdentifier = new DefaultBuildIdentifier(buildPath);
        // TODO: ensure the display name logic is shared with BuildIdentifier for consistency
        this.displayName = Describables.memoize(Describables.withTypeAndName("build", buildPath.asString()));
    }

    /**
     * The identity of the build within the build tree. This path is fixed for the lifetime of the build.
     */
    public Path getBuildPath() {
        return buildPath;
    }

    /**
     * The public identifier for this build.
     */
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    /**
     * Returns the display name of this build, such as {@code build ':'} or {@code build ':included'}.
     */
    @Override
    public String getDisplayName() {
        return displayName.getDisplayName();
    }

    @Override
    public String getCapitalizedDisplayName() {
        return displayName.getCapitalizedDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildIdentity that = (BuildIdentity) o;
        return buildPath.equals(that.buildPath);
    }

    @Override
    public int hashCode() {
        return buildPath.hashCode();
    }
}
