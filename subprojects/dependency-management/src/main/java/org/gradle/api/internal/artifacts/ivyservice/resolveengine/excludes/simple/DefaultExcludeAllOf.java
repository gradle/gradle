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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAllOf;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.internal.component.model.IvyArtifactName;

final class DefaultExcludeAllOf extends DefaultCompositeExclude implements ExcludeAllOf {
    public static ExcludeAllOf of(ImmutableSet<ExcludeSpec> components) {
        return new DefaultExcludeAllOf(components);
    }

    private DefaultExcludeAllOf(ImmutableSet<ExcludeSpec> components) {
        super(components);
    }

    @Override
    int mask() {
        return 1877062907;
    }

    private Boolean mayExcludeArtifacts;

    @Override
    protected String getDisplayName() {
        return "all of";
    }

    @Override
    public boolean excludes(ModuleIdentifier module) {
        return components().allMatch(e -> e.excludes(module));
    }

    @Override
    public boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifactName) {
        return components().allMatch(e -> e.excludesArtifact(module, artifactName));
    }

    @Override
    public boolean mayExcludeArtifacts() {
        if (mayExcludeArtifacts != null) {
            return mayExcludeArtifacts;
        }
        mayExcludeArtifacts = components().allMatch(ExcludeSpec::mayExcludeArtifacts);
        return mayExcludeArtifacts;
    }

}
