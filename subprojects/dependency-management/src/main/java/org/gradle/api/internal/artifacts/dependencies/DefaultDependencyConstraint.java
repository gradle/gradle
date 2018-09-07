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
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionSelectorStrictSpec;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nullable;

public class DefaultDependencyConstraint implements DependencyConstraintInternal {

    private final static Logger LOG = Logging.getLogger(DefaultDependencyConstraint.class);

    private final ModuleIdentifier moduleIdentifier;
    private final MutableVersionConstraint versionConstraint;

    private String reason;
    private ImmutableAttributesFactory attributesFactory;
    private AttributeContainerInternal attributes;
    private boolean force;

    public DefaultDependencyConstraint(String group, String name, String version) {
        this.moduleIdentifier = DefaultModuleIdentifier.newId(group, name);
        this.versionConstraint = new DefaultMutableVersionConstraint(version);
    }

    private DefaultDependencyConstraint(ModuleIdentifier module, MutableVersionConstraint versionConstraint) {
        this.moduleIdentifier = module;
        this.versionConstraint = versionConstraint;
    }

    @Nullable
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
        return Strings.emptyToNull(versionConstraint.getRequiredVersion());
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes == null ? ImmutableAttributes.EMPTY : attributes.asImmutable();
    }

    @Override
    public DependencyConstraint attributes(Action<? super AttributeContainer> configureAction) {
        if (attributesFactory == null) {
            warnAboutInternalApiUse();
            return this;
        }
        if (attributes == null) {
            attributes = attributesFactory.mutable();
        }
        configureAction.execute(attributes);
        return this;
    }

    private void warnAboutInternalApiUse() {
        LOG.warn("Cannot set attributes for constraint \"" + this.getGroup() + ":" + this.getName() + ":" + this.getVersion() + "\": it was probably created by a plugin using internal APIs");
    }

    public void setAttributesFactory(ImmutableAttributesFactory attributesFactory) {
        this.attributesFactory = attributesFactory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultDependencyConstraint that = (DefaultDependencyConstraint) o;
        return Objects.equal(moduleIdentifier, that.moduleIdentifier) &&
            Objects.equal(versionConstraint, that.versionConstraint) &&
            Objects.equal(attributes, that.attributes) &&
            force == that.force;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(moduleIdentifier, versionConstraint, attributes);
    }

    @Override
    public void version(Action<? super MutableVersionConstraint> configureAction) {
        configureAction.execute(versionConstraint);
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return new ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier);
    }

    @Override
    public ModuleIdentifier getModule() {
        return moduleIdentifier;
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public void because(String reason) {
        this.reason = reason;
    }

    public DependencyConstraint copy() {
        DefaultDependencyConstraint constraint = new DefaultDependencyConstraint(moduleIdentifier, versionConstraint);
        constraint.reason = reason;
        constraint.attributes = attributes;
        constraint.attributesFactory = attributesFactory;
        constraint.force = force;
        return constraint;
    }

    @Override
    public String toString() {
        return "constraint " +
            moduleIdentifier + ":" + versionConstraint +
            ", attributes=" + attributes;
    }

    @Override
    public void setForce(boolean force) {
        this.force = force;
    }

    @Override
    public boolean isForce() {
        return force;
    }
}
