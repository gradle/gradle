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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;

public abstract class AbstractDependencyImpl<T extends DependencyMetadata<T>> implements DependencyMetadata<T> {
    private final ModuleIdentifier moduleIdentifier;
    private final MutableVersionConstraint versionConstraint;
    private String reason;
    private AttributeContainer attributes = ImmutableAttributes.EMPTY;

    public AbstractDependencyImpl(String group, String name, String version) {
        this.moduleIdentifier = DefaultModuleIdentifier.newId(group, name);
        this.versionConstraint = new DefaultMutableVersionConstraint(version);
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
    public VersionConstraint getVersionConstraint() {
        return this.versionConstraint;
    }

    @Override
    public ModuleIdentifier getModule() {
        return moduleIdentifier;
    }

    @Override
    public T version(Action<? super MutableVersionConstraint> configureAction) {
        configureAction.execute(versionConstraint);
        return Cast.uncheckedCast(this);
    }

    @Override
    public T attributes(Action<? super AttributeContainer> configureAction) {
        configureAction.execute(attributes);
        return Cast.uncheckedCast(this);
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public T because(String reason) {
        this.reason = reason;
        return Cast.uncheckedCast(this);
    }

    public void setAttributes(AttributeContainer attributes) {
        this.attributes = attributes;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return getGroup() + ":" + getName() + ":" + getVersionConstraint();
    }
}
