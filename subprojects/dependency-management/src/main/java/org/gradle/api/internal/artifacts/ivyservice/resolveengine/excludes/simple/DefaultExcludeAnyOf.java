/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.internal.component.model.IvyArtifactName;

final class DefaultExcludeAnyOf extends DefaultCompositeExclude implements ExcludeAnyOf {
    public static ExcludeSpec of(ImmutableSet<ExcludeSpec> components) {
        return new DefaultExcludeAnyOf(components);
    }

    private DefaultExcludeAnyOf(ImmutableSet<ExcludeSpec> components) {
        super(components);
    }

    @Override
    int mask() {
        return 1731217984;
    }

    private Boolean mayExcludeArtifacts;

    @Override
    protected String getDisplayName() {
        return "any of";
    }

    @Override
    public boolean excludes(ModuleIdentifier module) {
        return components().anyMatch(e -> e.excludes(module));
    }

    @Override
    public boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifactName) {
        return components().anyMatch(e -> e.excludesArtifact(module, artifactName));
    }

    @Override
    public boolean mayExcludeArtifacts() {
        if (mayExcludeArtifacts != null) {
            return mayExcludeArtifacts;
        }
        mayExcludeArtifacts = components().anyMatch(ExcludeSpec::mayExcludeArtifacts);
        return mayExcludeArtifacts;
    }
}
