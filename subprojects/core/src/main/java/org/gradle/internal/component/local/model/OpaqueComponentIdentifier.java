/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;

public class OpaqueComponentIdentifier implements ComponentIdentifier {
    private final DependencyFactoryInternal.ClassPathNotation classPathNotation;

    public OpaqueComponentIdentifier(DependencyFactoryInternal.ClassPathNotation classPathNotation) {
        assert classPathNotation != null;
        this.classPathNotation = classPathNotation;
    }

    @Override
    public String getDisplayName() {
        return classPathNotation.displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        OpaqueComponentIdentifier that = (OpaqueComponentIdentifier) o;

        return classPathNotation.equals(that.classPathNotation);
    }

    @Override
    public int hashCode() {
        return classPathNotation.hashCode();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public DependencyFactoryInternal.ClassPathNotation getClassPathNotation() {
        return classPathNotation;
    }
}
