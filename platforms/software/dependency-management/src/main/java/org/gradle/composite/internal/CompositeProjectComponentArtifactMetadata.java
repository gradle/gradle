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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

/**
 * Represents an artifact that has its owning component ID overridden.
 * <p>
 * This is used when an artifact is referenced from another build, and its owning component id is overridden to its foreign counterpart.
 * This will go away in Gradle 9.0, when we no longer need to override the owning component id with a foreign identifier.
 * <p>
 * This should be better named as {@code OverriddenComponentIdArtifactMetadata} or similar, but Kotlin currently references this internal class.
 *
 * @see <a href="https://github.com/JetBrains/kotlin/blame/83a7ecda3eccd378a3882b81d8e83ecc12b3b786/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/npm/resolver/KotlinCompilationNpmResolver.kt#L245">Link</a>
 *
 * @deprecated This class will be removed in Gradle 9.0.
 */
@Deprecated
public class CompositeProjectComponentArtifactMetadata implements LocalComponentArtifactMetadata, ComponentArtifactIdentifier {

    private final ComponentIdentifier overrideComponentId;
    private final LocalComponentArtifactMetadata delegate;

    public CompositeProjectComponentArtifactMetadata(
        ComponentIdentifier overrideComponentId,
        LocalComponentArtifactMetadata delegate
    ) {
        this.overrideComponentId = overrideComponentId;
        this.delegate = delegate;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    public LocalComponentArtifactMetadata getDelegate() {
        return delegate;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return overrideComponentId;
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return this;
    }

    @Override
    public IvyArtifactName getName() {
        return delegate.getName();
    }

    @Override
    public ComponentIdentifier getComponentIdentifier() {
        return overrideComponentId;
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public String getCapitalizedDisplayName() {
        return delegate.getCapitalizedDisplayName();
    }

    @Override
    public File getFile() {
        return delegate.getFile();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return delegate.getBuildDependencies();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompositeProjectComponentArtifactMetadata)) {
            return false;
        }

        CompositeProjectComponentArtifactMetadata that = (CompositeProjectComponentArtifactMetadata) o;
        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
