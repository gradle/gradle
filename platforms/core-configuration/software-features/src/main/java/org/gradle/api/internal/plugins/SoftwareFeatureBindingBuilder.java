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


public interface SoftwareFeatureBindingBuilder {
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
}

