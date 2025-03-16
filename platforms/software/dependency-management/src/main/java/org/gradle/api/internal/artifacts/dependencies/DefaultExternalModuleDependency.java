/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jspecify.annotations.Nullable;

public class DefaultExternalModuleDependency extends AbstractExternalModuleDependency implements ExternalModuleDependency {

    public DefaultExternalModuleDependency(String group, String name, String version) {
        this(group, name, version, null);
    }

    public DefaultExternalModuleDependency(String group, String name, String version, @Nullable String configuration) {
        super(assertModuleId(group, name), version, configuration);
    }

    public DefaultExternalModuleDependency(ModuleIdentifier id, MutableVersionConstraint versionConstraint, @Nullable String configuration) {
        super(id, versionConstraint, configuration);
    }

    @Override
    public ExternalModuleDependency copy() {
        DefaultExternalModuleDependency copiedModuleDependency = new DefaultExternalModuleDependency(getModule(), new DefaultMutableVersionConstraint(getVersionConstraint()), getTargetConfiguration());
        copyTo(copiedModuleDependency);
        return copiedModuleDependency;
    }

    @Override
    @Deprecated
    public boolean contentEquals(Dependency dependency) {

        DeprecationLogger.deprecateMethod(Dependency.class, "contentEquals(Dependency)")
            .withAdvice("Use Object.equals(Object) instead")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_content_equals")
            .nagUser();

        if (this == dependency) {
            return true;
        }
        if (dependency == null || getClass() != dependency.getClass()) {
            return false;
        }

        ExternalModuleDependency that = (ExternalModuleDependency) dependency;
        return isContentEqualsFor(that);

    }
}
