/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.util.internal.WrapUtil;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class DefaultDependencyMetadataFactory implements DependencyMetadataFactory {
    private final List<DependencyMetadataConverter> dependencyDescriptorFactories;

    public DefaultDependencyMetadataFactory(DependencyMetadataConverter... dependencyDescriptorFactories) {
        this.dependencyDescriptorFactories = WrapUtil.toList(dependencyDescriptorFactories);
    }

    @Override
    public LocalOriginDependencyMetadata createDependencyMetadata(ModuleDependency dependency) {
        DependencyMetadataConverter factoryInternal = findFactoryForDependency(dependency);
        return factoryInternal.createDependencyMetadata(dependency);
    }

    @Override
    public LocalOriginDependencyMetadata createDependencyConstraintMetadata(DependencyConstraint dependencyConstraint) {
        ComponentSelector selector = createSelector(dependencyConstraint);
        return new LocalComponentDependencyMetadata(selector, null,
            Collections.emptyList(), Collections.emptyList(), ((DependencyConstraintInternal) dependencyConstraint).isForce(), false, false, true, false, dependencyConstraint.getReason());
    }

    private ComponentSelector createSelector(DependencyConstraint dependencyConstraint) {
        if (dependencyConstraint instanceof DefaultProjectDependencyConstraint) {
            ProjectDependencyInternal projectDependency = (ProjectDependencyInternal) ((DefaultProjectDependencyConstraint) dependencyConstraint).getProjectDependency();

            return new DefaultProjectComponentSelector(
                projectDependency.getTargetProjectIdentity(),
                ((ImmutableAttributes) projectDependency.getAttributes()).asImmutable(),
                Collections.emptyList()
            );
        }

        return DefaultModuleComponentSelector.newSelector(
            DefaultModuleIdentifier.newId(nullToEmpty(dependencyConstraint.getGroup()), nullToEmpty(dependencyConstraint.getName())), dependencyConstraint.getVersionConstraint(), dependencyConstraint.getAttributes(), ImmutableList.of());
    }

    private DependencyMetadataConverter findFactoryForDependency(ModuleDependency dependency) {
        for (DependencyMetadataConverter dependencyMetadataConverter : dependencyDescriptorFactories) {
            if (dependencyMetadataConverter.canConvert(dependency)) {
                return dependencyMetadataConverter;
            }
        }
        throw new InvalidUserDataException("Can't map dependency of type: " + dependency.getClass());
    }

    private String nullToEmpty(@Nullable String input) {
        return input == null ? "" : input;
    }
}
