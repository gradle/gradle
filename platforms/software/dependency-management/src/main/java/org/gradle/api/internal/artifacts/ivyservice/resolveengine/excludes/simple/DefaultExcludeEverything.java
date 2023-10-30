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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything;
import org.gradle.internal.component.model.IvyArtifactName;

final class DefaultExcludeEverything implements ExcludeEverything {
    private static final ExcludeEverything INSTANCE = new DefaultExcludeEverything();

    public static ExcludeEverything get() {
        return INSTANCE;
    }

    private DefaultExcludeEverything() {
    }

    @Override
    public boolean excludes(ModuleIdentifier module) {
        return true;
    }

    @Override
    public boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifactName) {
        // everything excluded **only** applies to modules, not artifacts!
        return false;
    }

    @Override
    public boolean mayExcludeArtifacts() {
        return false; // an exclude all is for modules, not artifacts
    }

    @Override
    public String toString() {
        return "\"excludes everything\"";
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    private int size() {
        return 1;
    }
}
