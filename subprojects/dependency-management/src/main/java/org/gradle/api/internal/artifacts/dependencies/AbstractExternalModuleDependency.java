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

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ModuleVersionSelectorStrictSpec;

public abstract class AbstractExternalModuleDependency extends AbstractModuleDependency implements ExternalModuleDependency {
    private String group;
    private String name;
    private boolean changing;
    private boolean force;
    private final MutableVersionConstraint versionConstraint;

    public AbstractExternalModuleDependency(String group, String name, String version, String configuration) {
        super(configuration);
        if (name == null) {
            throw new InvalidUserDataException("Name must not be null!");
        }
        this.group = group;
        this.name = name;
        this.versionConstraint = new DefaultMutableVersionConstraint(version);
    }

    protected void copyTo(AbstractExternalModuleDependency target) {
        super.copyTo(target);
        target.setForce(isForce());
        target.setChanging(isChanging());
    }

    protected boolean isContentEqualsFor(ExternalModuleDependency dependencyRhs) {
        if (!isKeyEquals(dependencyRhs) || !isCommonContentEquals(dependencyRhs)) {
            return false;
        }
        return force == dependencyRhs.isForce() && changing == dependencyRhs.isChanging();
    }

    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return versionConstraint.getPreferredVersion();
    }

    public boolean isForce() {
        return force;
    }

    public ExternalModuleDependency setForce(boolean force) {
        validateMutation(this.force, force);
        this.force = force;
        return this;
    }

    public boolean isChanging() {
        return changing;
    }

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
}
