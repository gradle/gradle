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
 * A builder for creating bindings between software feature definition objects
 * and other definition objects in the build as well as declaring build logic
 * associated with the binding.
 */
public interface SoftwareFeatureBindingBuilder {
    /**
     * Create a binding between a software feature definition object and a parent definition object.
     * The supplied transform is used to implement the build logic associated with the binding.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param bindingTypeInformation type information about the parent object the feature can be bound to
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <Definition> the type of the software definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetDefinition> the type of the parent definition object this feature can be bound to
     */
    <
        Definition extends HasBuildModel<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends HasBuildModel<?>
        >
    DslBindingBuilder<Definition, OwnBuildModel> bindSoftwareFeature(
        String name,
        ModelBindingTypeInformation<Definition, OwnBuildModel, TargetDefinition> bindingTypeInformation,
        SoftwareFeatureTransform<Definition, OwnBuildModel, TargetDefinition> transform
    );

    /**
     * A convenience method for creating a binding between a software feature definition object
     * and a parent definition object.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param definitionClass the class of the software feature definition object
     * @param targetDefinitionClass the class of the parent definition object this feature can be bound to
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <Definition> the type of the software definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetDefinition> the type of the parent definition object this feature can be bound to
     */
    default <
        Definition extends HasBuildModel<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends HasBuildModel<?>
        >
    DslBindingBuilder<Definition, OwnBuildModel> bindSoftwareFeatureToDefinition(
        String name,
        Class<Definition> definitionClass,
        Class<TargetDefinition> targetDefinitionClass,
        SoftwareFeatureTransform<Definition, OwnBuildModel, TargetDefinition> transform
    ) {
        return bindSoftwareFeature(name, bindingToTargetDefinition(definitionClass, targetDefinitionClass), transform);
    }

    /**
     * A convenience method for creating a binding between a software feature definition object
     * and a parent definition object that has a specific build model type.
     *
     * @param name the name of the binding.  This is how it will be referenced in the DSL.
     * @param definitionClass the class of the software feature definition object
     * @param targetBuildModelClass the class of the build model type of the parent definition object this feature can be bound to
     * @param transform the transform that maps the definition to the build model and implements the build logic associated with the feature
     * @return a {@link DslBindingBuilder} that can be used to further configure the binding
     * @param <Definition> the type of the software definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetBuildModel> the type of the build model type of the parent definition object this feature can be bound to
     */
    default <
        Definition extends HasBuildModel<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetBuildModel extends BuildModel
        >
    DslBindingBuilder<Definition, OwnBuildModel> bindSoftwareFeatureToBuildModel(
        String name,
        Class<Definition> definitionClass,
        Class<TargetBuildModel> targetBuildModelClass,
        SoftwareFeatureTransform<Definition, OwnBuildModel, HasBuildModel<TargetBuildModel>> transform
    ) {
        return bindSoftwareFeature(name, bindingToTargetBuildModel(definitionClass, targetBuildModelClass), transform);
    }

    /**
     * A convenience method for creating type information about a binding between
     * a software feature definition object and a parent definition object.
     *
     * @param definition the class of the software feature definition object
     * @param targetDefinition the class of the parent definition object this feature can be bound to
     * @return type information about the binding
     * @param <Definition> the type of the software definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetDefinition> the type of the parent definition object this feature can be bound to
     */
    static <
        Definition extends HasBuildModel<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends HasBuildModel<?>
        >
    ModelBindingTypeInformation<Definition, OwnBuildModel, TargetDefinition> bindingToTargetDefinition(
        Class<Definition> definition,
        Class<TargetDefinition> targetDefinition
    ) {
        return new ModelBindingTypeInformation<>(definition, new DefinitionTargetTypeInformation<>(targetDefinition));
    }

    /**
     * A convenience method for creating type information about a binding between
     * a software feature definition object and a parent definition object that has
     * a specific build model type.
     *
     * @param definition the class of the software feature definition object
     * @param targetBuildModel the class of the build model type of the parent definition object this feature can be bound to
     * @return type information about the binding
     * @param <Definition> the type of the software definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetBuildModel> the type of the build model type of the parent definition object this feature can be bound to
     */
    static <
        Definition extends HasBuildModel<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetBuildModel extends BuildModel
        >
    ModelBindingTypeInformation<Definition, OwnBuildModel, HasBuildModel<TargetBuildModel>> bindingToTargetBuildModel(
        Class<Definition> definition,
        Class<TargetBuildModel> targetBuildModel
    ) {
        return new ModelBindingTypeInformation<>(definition, new BuildModelTargetTypeInformation<>(targetBuildModel));
    }

    /**
     * Type information about a binding between a software feature definition object
     * and a parent definition object in the build.
     *
     * @param <Definition> the type of the software definition object for this feature
     * @param <OwnBuildModel> the type of the build model object for this feature
     * @param <TargetDefinition> the type of the parent definition object this feature can be bound to
     */
    class ModelBindingTypeInformation<
        Definition extends HasBuildModel<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends HasBuildModel<?>
        > {

        private final Class<Definition> definitionType;
        private final TargetTypeInformation<TargetDefinition> targetType;

        public ModelBindingTypeInformation(
            Class<Definition> definitionType,
            TargetTypeInformation<TargetDefinition> targetType
        ) {
            this.definitionType = definitionType;
            this.targetType = targetType;
        }

        public Class<Definition> getDefinitionType() {
            return definitionType;
        }

        public TargetTypeInformation<TargetDefinition> getTargetType() {
            return targetType;
        }
    }
}

