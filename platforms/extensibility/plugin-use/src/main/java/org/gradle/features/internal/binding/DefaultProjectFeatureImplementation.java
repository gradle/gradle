/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.binding;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.Definition;
import org.gradle.features.binding.TargetTypeInformation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a resolved project type implementation.  Used by declarative DSL to understand which model types should be exposed for
 * which project types.
 */
public class DefaultProjectFeatureImplementation<OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> implements ProjectFeatureImplementation<OwnDefinition, OwnBuildModel> {
    private final String featureName;
    private final Class<OwnDefinition> definitionPublicType;
    private final Class<? extends OwnDefinition> definitionImplementationType;
    private final ProjectFeatureBindingDeclaration.Safety definitionSafety;
    private final ProjectFeatureBindingDeclaration.Safety applyActionSafety;
    private final TargetTypeInformation<?> targetDefinitionType;
    private final Class<OwnBuildModel> buildModelType;
    private final Class<? extends OwnBuildModel> buildModelImplementationType;
    private final Class<? extends Plugin<Project>> pluginClass;
    private final Class<? extends Plugin<Settings>> registeringPluginClass;
    private final List<ModelDefault<?>> defaults = new ArrayList<>();
    @Nullable
    private final String registeringPluginId;
    private final ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, ?> applyActionFactory;

    public DefaultProjectFeatureImplementation(
        String featureName,
        Class<OwnDefinition> definitionPublicType,
        Class<? extends OwnDefinition> definitionImplementationType,
        ProjectFeatureBindingDeclaration.Safety definitionSafety,
        ProjectFeatureBindingDeclaration.Safety applyActionSafety,
        TargetTypeInformation<?> targetDefinitionType,
        Class<OwnBuildModel> buildModelType,
        Class<? extends OwnBuildModel> buildModelImplementationType,
        Class<? extends Plugin<Project>> pluginClass,
        Class<? extends Plugin<Settings>> registeringPluginClass,
        @Nullable String registeringPluginId,
        ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, ?> applyActionFactory
    ) {
        this.featureName = featureName;
        this.definitionPublicType = definitionPublicType;
        this.definitionImplementationType = definitionImplementationType;
        this.definitionSafety = definitionSafety;
        this.applyActionSafety = applyActionSafety;
        this.targetDefinitionType = targetDefinitionType;
        this.buildModelType = buildModelType;
        this.buildModelImplementationType = buildModelImplementationType;
        this.pluginClass = pluginClass;
        this.registeringPluginClass = registeringPluginClass;
        this.registeringPluginId = registeringPluginId;
        this.applyActionFactory = applyActionFactory;
    }

    @Override
    public String getFeatureName() {
        return featureName;
    }

    @Override
    public Class<OwnDefinition> getDefinitionPublicType() {
        return definitionPublicType;
    }

    @Override
    public Class<? extends OwnDefinition> getDefinitionImplementationType() {
        return definitionImplementationType;
    }

    @Override
    public ProjectFeatureBindingDeclaration.Safety getDefinitionSafety() {
        return definitionSafety;
    }

    @Override
    public ProjectFeatureBindingDeclaration.Safety getApplyActionSafety() {
        return applyActionSafety;
    }

    @Override
    public Class<OwnBuildModel> getBuildModelType() {
        return buildModelType;
    }

    @Override
    public Class<? extends OwnBuildModel> getBuildModelImplementationType() {
        return buildModelImplementationType;
    }

    @Override
    public TargetTypeInformation<?> getTargetDefinitionType() {
        return targetDefinitionType;
    }

    @Override
    public Class<? extends Plugin<Project>> getPluginClass() {
        return pluginClass;
    }

    @Override
    public Class<? extends Plugin<Settings>> getRegisteringPluginClass() {
        return registeringPluginClass;
    }

    @Nullable
    @Override
    public String getRegisteringPluginId() {
        return registeringPluginId;
    }

    @Override
    public ProjectFeatureApplyActionFactory<OwnDefinition, OwnBuildModel, ?> getApplyActionFactory() {
        return applyActionFactory;
    }

    @Override
    public void addModelDefault(ModelDefault<?> modelDefault) {
        defaults.add(modelDefault);
    }

    @Override
    public <M extends ModelDefault.Visitor<?>> void visitModelDefaults(Class<? extends ModelDefault<M>> type, M visitor) {
        defaults.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .forEach(modelDefault -> modelDefault.visit(visitor));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultProjectFeatureImplementation<?, ?> that = (DefaultProjectFeatureImplementation<?, ?>) o;
        return Objects.equals(featureName, that.featureName) && Objects.equals(definitionPublicType, that.definitionPublicType) && Objects.equals(pluginClass, that.pluginClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureName, definitionPublicType, pluginClass);
    }
}
