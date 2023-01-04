/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionSelectorStrictSpec;

import javax.annotation.Nullable;

public abstract class AbstractExternalModuleDependency extends AbstractModuleDependency implements ExternalModuleDependency {
    private final ModuleIdentifier moduleIdentifier;
    private boolean changing;
    private final DefaultMutableVersionConstraint versionConstraint;

    public AbstractExternalModuleDependency(ModuleIdentifier module, String version, @Nullable String configuration) {
        super(configuration);
        if (module == null) {
            throw new InvalidUserDataException("Module must not be null!");
        }
        this.moduleIdentifier = module;
        this.versionConstraint = new DefaultMutableVersionConstraint(version);
    }

    public AbstractExternalModuleDependency(ModuleIdentifier module, MutableVersionConstraint version, @Nullable String configuration) {
        super(configuration);
        if (module == null) {
            throw new InvalidUserDataException("Module must not be null!");
        }
        this.moduleIdentifier = module;
        this.versionConstraint = (DefaultMutableVersionConstraint) version;
    }

    protected void copyTo(AbstractExternalModuleDependency target) {
        super.copyTo(target);
        target.setChanging(isChanging());
    }

    protected boolean isContentEqualsFor(ExternalModuleDependency dependencyRhs) {
        if (!isCommonContentEquals(dependencyRhs)) {
            return false;
        }
        return changing == dependencyRhs.isChanging() &&
                Objects.equal(getVersionConstraint(), dependencyRhs.getVersionConstraint());
    }

    @Override
    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    @Override
    public String getGroup() {
        return moduleIdentifier.getGroup();
    }

    @Override
    public String getName() {
        return moduleIdentifier.getName();
    }

    @Override
    public String getVersion() {
        return Strings.emptyToNull(versionConstraint.getVersion());
    }

    @Override
    public boolean isForce() {
        return false; // Enforced Platforms no longer mark force, so there is no way for a dependency to be forced (configurations and resolution strategies are used instead)
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public ExternalModuleDependency setChanging(boolean changing) {
        validateMutation(this.changing, changing);
        this.changing = changing;
        return this;
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public void version(Action<? super MutableVersionConstraint> configureAction) {
        validateMutation();
        configureAction.execute(versionConstraint);
    }

    @Override
    public ModuleIdentifier getModule() {
        return moduleIdentifier;
    }

    static ModuleIdentifier assertModuleId(@Nullable String group, @Nullable String name) {
        if (name == null) {
            throw new InvalidUserDataException("Name must not be null!");
        }
        return DefaultModuleIdentifier.newId(group, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractExternalModuleDependency that = (AbstractExternalModuleDependency) o;
        return isContentEqualsFor(that);
    }

    @Override
    public int hashCode() {
        int result = getGroup() != null ? getGroup().hashCode() : 0;
        result = 31 * result + getName().hashCode();
        result = 31 * result + (getVersion() != null ? getVersion().hashCode() : 0);
        return result;
    }
}
