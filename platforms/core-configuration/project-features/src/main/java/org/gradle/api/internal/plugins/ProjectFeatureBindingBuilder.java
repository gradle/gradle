/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.api.internal.plugins.TargetTypeInformation.BuildModelTargetTypeInformation;
import org.gradle.api.internal.plugins.TargetTypeInformation.DefinitionTargetTypeInformation;

/**
 * A builder for creating bindings between project feature definition objects
 * and other definition objects in the build as well as declaring build logic
 * associated with the binding.
 */
public interface ProjectFeatureBindingBuilder {
    /**
     * Create a binding between a project feature definition object and a parent definition object.
     * The supplied transform is used to implement the build logic associated with the binding.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param bindingTypeInformation type information about the parent object the feature can be bound to
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <OwnDefinition> the type of the definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetDefinition> the type of the parent definition object this feature can be bound to
     */
    <
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends Definition<?>
        >
    DslBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectFeature(
        String name,
        ModelBindingTypeInformation<OwnDefinition, OwnBuildModel, TargetDefinition> bindingTypeInformation,
        ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, TargetDefinition> transform
    );

    /**
     * A convenience method for creating a binding between a project feature definition object
     * and a parent definition object.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param definitionClass the class of the project feature definition object
     * @param targetDefinitionClass the class of the parent definition object this feature can be bound to
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <OwnDefinition> the type of the definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetDefinition> the type of the parent definition object this feature can be bound to
     */
    default <
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends Definition<?>
        >
    DslBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectFeatureToDefinition(
        String name,
        Class<OwnDefinition> definitionClass,
        Class<TargetDefinition> targetDefinitionClass,
        ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, TargetDefinition> transform
    ) {
        return bindProjectFeature(name, bindingToTargetDefinition(definitionClass, targetDefinitionClass), transform);
    }

    /**
     * A convenience method for creating a binding between a project feature definition object
     * and a parent definition object that has a specific build model type.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param definitionClass the class of the project feature definition object
     * @param targetBuildModelClass the class of the build model type of the parent definition object this feature can be bound to
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <OwnDefinition> the type of the definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetBuildModel> the type of the build model type of the parent definition object this feature can be bound to
     */
    default <
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetBuildModel extends BuildModel
        >
    DslBindingBuilder<OwnDefinition, OwnBuildModel> bindProjectFeatureToBuildModel(
        String name,
        Class<OwnDefinition> definitionClass,
        Class<TargetBuildModel> targetBuildModelClass,
        ProjectFeatureApplyAction<OwnDefinition, OwnBuildModel, Definition<TargetBuildModel>> transform
    ) {
        return bindProjectFeature(name, bindingToTargetBuildModel(definitionClass, targetBuildModelClass), transform);
    }

    /**
     * A convenience method for creating type information about a binding between
     * a project feature definition object and a parent definition object.
     *
     * @param definition the class of the project feature definition object
     * @param targetDefinition the class of the parent definition object this feature can be bound to
     * @return type information about the binding
     * @param <OwnDefinition> the type of the definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetDefinition> the type of the parent definition object this feature can be bound to
     */
    static <
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends Definition<?>
        >
    ModelBindingTypeInformation<OwnDefinition, OwnBuildModel, TargetDefinition> bindingToTargetDefinition(
        Class<OwnDefinition> definition,
        Class<TargetDefinition> targetDefinition
    ) {
        return new ModelBindingTypeInformation<>(definition, new DefinitionTargetTypeInformation<>(targetDefinition));
    }

    /**
     * A convenience method for creating type information about a binding between
     * a project feature definition object and a parent definition object that has
     * a specific build model type.
     *
     * @param definition the class of the project feature definition object
     * @param targetBuildModel the class of the build model type of the parent definition object this feature can be bound to
     * @return type information about the binding
     * @param <OwnDefinition> the type of the definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetBuildModel> the type of the build model type of the parent definition object this feature can be bound to
     */
    static <
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetBuildModel extends BuildModel
        >
    ModelBindingTypeInformation<OwnDefinition, OwnBuildModel, Definition<TargetBuildModel>> bindingToTargetBuildModel(
        Class<OwnDefinition> definition,
        Class<TargetBuildModel> targetBuildModel
    ) {
        return new ModelBindingTypeInformation<>(definition, new BuildModelTargetTypeInformation<>(targetBuildModel));
    }

    /**
     * Type information about a binding between a project feature definition object
     * and a parent definition object in the build.
     *
     * @param <OwnDefinition> the type of the definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetDefinition> the type of the parent definition object this feature can be bound to
     */
    class ModelBindingTypeInformation<
        OwnDefinition extends Definition<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends Definition<?>
        > {

        private final Class<OwnDefinition> definitionType;
        private final TargetTypeInformation<TargetDefinition> targetType;

        public ModelBindingTypeInformation(
            Class<OwnDefinition> definitionType,
            TargetTypeInformation<TargetDefinition> targetType
        ) {
            this.definitionType = definitionType;
            this.targetType = targetType;
        }

        public Class<OwnDefinition> getDefinitionType() {
            return definitionType;
        }

        public TargetTypeInformation<TargetDefinition> getTargetType() {
            return targetType;
        }
    }
}

