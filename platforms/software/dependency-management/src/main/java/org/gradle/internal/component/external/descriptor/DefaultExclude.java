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

package org.gradle.internal.component.external.descriptor;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;

public class DefaultExclude implements Exclude {
    private final ModuleIdentifier moduleId;
    private final IvyArtifactName artifact;
    private final Set<String> configurations;
    private final String patternMatcher;

    public DefaultExclude(ModuleIdentifier id, IvyArtifactName artifact, String[] configurations, @Nullable String patternMatcher) {
        this.moduleId = id;
        this.artifact = artifact;
        this.patternMatcher = patternMatcher;
        this.configurations = ImmutableSet.copyOf(configurations);
    }

    public DefaultExclude(ModuleIdentifier id, String[] configurations, @Nullable String patternMatcher) {
        this.moduleId = id;
        this.artifact = null;
        this.patternMatcher = patternMatcher;
        this.configurations = ImmutableSet.copyOf(configurations);
    }

    public DefaultExclude(ModuleIdentifier id) {
        this.moduleId = id;
        this.artifact = null;
        this.patternMatcher = null;
        this.configurations = ImmutableSet.of();
    }

    @Override
    public String toString() {
        return "{exclude moduleId: " + moduleId + ", artifact: " + artifact + "}";
    }

    @Override
    public ModuleIdentifier getModuleId() {
        return moduleId;
    }

    @Override
    public IvyArtifactName getArtifact() {
        return artifact;
    }

    @Override
    public Set<String> getConfigurations() {
        return configurations;
    }

    @Override
    public String getMatcher() {
        return patternMatcher;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultExclude that = (DefaultExclude) o;

        if (!moduleId.equals(that.moduleId)) {
            return false;
        }
        if (!Objects.equals(artifact, that.artifact)) {
            return false;
        }
        if (!configurations.equals(that.configurations)) {
            return false;
        }
        return Objects.equals(patternMatcher, that.patternMatcher);
    }

    @Override
    public int hashCode() {
        int result = moduleId.hashCode();
        result = 31 * result + (artifact != null ? artifact.hashCode() : 0);
        result = 31 * result + configurations.hashCode();
        result = 31 * result + (patternMatcher != null ? patternMatcher.hashCode() : 0);
        return result;
    }
}
