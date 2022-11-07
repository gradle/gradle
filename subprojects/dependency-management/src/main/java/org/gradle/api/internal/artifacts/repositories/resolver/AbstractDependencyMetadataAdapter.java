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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;

public abstract class AbstractDependencyMetadataAdapter<T extends DependencyMetadata<T>> implements DependencyMetadata<T> {
    private final ImmutableAttributesFactory attributesFactory;
    private ModuleDependencyMetadata metadata;

    public AbstractDependencyMetadataAdapter(ImmutableAttributesFactory attributesFactory, ModuleDependencyMetadata metadata) {
        this.attributesFactory = attributesFactory;
        this.metadata = metadata;
    }

    public ModuleDependencyMetadata getMetadata() {
        return metadata;
    }

    protected void updateMetadata(ModuleDependencyMetadata modifiedMetadata) {
        this.metadata = modifiedMetadata;
    }

    @Override
    public String getGroup() {
        return getMetadata().getSelector().getGroup();
    }

    @Override
    public String getName() {
        return getMetadata().getSelector().getModule();
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return getMetadata().getSelector().getVersionConstraint();
    }

    @Override
    public T version(Action<? super MutableVersionConstraint> configureAction) {
        DefaultMutableVersionConstraint mutableVersionConstraint = new DefaultMutableVersionConstraint(getVersionConstraint());
        configureAction.execute(mutableVersionConstraint);
        updateMetadata(getMetadata().withRequestedVersion(mutableVersionConstraint));
        return Cast.uncheckedCast(this);
    }

    @Override
    public T because(String reason) {
        updateMetadata(getMetadata().withReason(reason));
        return Cast.uncheckedCast(this);
    }

    @Override
    public ModuleIdentifier getModule() {
        return getMetadata().getSelector().getModuleIdentifier();
    }

    @Override
    public String getReason() {
        return getMetadata().getReason();
    }

    @Override
    public String toString() {
        return getGroup() + ":" + getName() + ":" + getVersionConstraint();
    }

    @Override
    public AttributeContainer getAttributes() {
        return getMetadata().getSelector().getAttributes();
    }

    @Override
    public T attributes(Action<? super AttributeContainer> configureAction) {
        ModuleComponentSelector selector = getMetadata().getSelector();
        AttributeContainerInternal attributes = attributesFactory.mutable((AttributeContainerInternal) selector.getAttributes());
        configureAction.execute(attributes);
        ModuleComponentSelector target = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), selector.getVersionConstraint(), attributes.asImmutable(), selector.getRequestedCapabilities());
        ModuleDependencyMetadata metadata = (ModuleDependencyMetadata) getMetadata().withTarget(target);
        updateMetadata(metadata);
        return Cast.uncheckedCast(this);
    }

    public void forced() {
        ModuleDependencyMetadata originalMetadata = getMetadata();
        if (originalMetadata instanceof ForcingDependencyMetadata) {
            updateMetadata((ModuleDependencyMetadata) ((ForcingDependencyMetadata) originalMetadata).forced());
        }
    }
}
