/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionSelectorStrictSpec;

public abstract class AbstractExternalDependency extends AbstractModuleDependency implements ExternalDependency {
    public AbstractExternalDependency(String configuration) {
        super(configuration);
    }

    protected void copyTo(AbstractExternalDependency target) {
        super.copyTo(target);
        target.setForce(isForce());
    }

    protected boolean isContentEqualsFor(ExternalDependency dependencyRhs) {
        if (!isKeyEquals(dependencyRhs) || !isCommonContentEquals(dependencyRhs)) {
            return false;
        }
        return isForce() == dependencyRhs.isForce();
    }

    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }
}
