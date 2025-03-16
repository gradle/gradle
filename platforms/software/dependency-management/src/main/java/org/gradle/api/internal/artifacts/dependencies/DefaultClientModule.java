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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("deprecation")
public class DefaultClientModule extends AbstractExternalModuleDependency implements org.gradle.api.artifacts.ClientModule {

    private final Set<ModuleDependency> dependencies = new LinkedHashSet<>();

    public DefaultClientModule(String group, String name, String version) {
        this(group, name, version, null);
    }

    public DefaultClientModule(String group, String name, String version, @Nullable String configuration) {
        super(assertModuleId(group, name), version, configuration);
    }

    @Override
    public String getId() {
        return emptyStringIfNull(getGroup()) + ":" + getName() + ":" + emptyStringIfNull(getVersion());
    }

    private String emptyStringIfNull(@Nullable String value) {
        return value == null ? "" : value;
    }

    @Override
    public Set<ModuleDependency> getDependencies() {
        return dependencies;
    }

    @Override
    public void addDependency(ModuleDependency dependency) {
        this.dependencies.add(dependency);
    }

    @Override
    public org.gradle.api.artifacts.ClientModule copy() {
        DefaultClientModule copiedClientModule = new DefaultClientModule(getGroup(), getName(), getVersion(), getTargetConfiguration());
        copyTo(copiedClientModule);
        for (ModuleDependency dependency : dependencies) {
            copiedClientModule.addDependency(dependency.copy());
        }
        return copiedClientModule;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Dependency)) {
            return false;
        }

        return DeprecationLogger.whileDisabled(() -> contentEquals((Dependency) o));
    }

    @Override
    public int hashCode() {
        return super.hashCode();
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

        org.gradle.api.artifacts.ClientModule that = (org.gradle.api.artifacts.ClientModule) dependency;
        return isContentEqualsFor(that) && dependencies.equals(that.getDependencies());
    }
}
