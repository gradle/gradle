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
import org.gradle.internal.inspection.DefaultTypeParameterInspection;
import org.gradle.internal.inspection.TypeParameterInspection;

import javax.annotation.Nonnull;
import java.util.List;

public interface SoftwareFeatureBindingBuilder {
    <
        Definition extends HasBuildModel<OwnBuildModel>,
        TargetDefinition extends HasBuildModel<?>,
        OwnBuildModel extends BuildModel
        > DslBindingBuilder<Definition, OwnBuildModel> bindSoftwareFeature(
        String name,
        ModelBindingTypeInformation<Definition, OwnBuildModel, TargetDefinition> bindingTypeInformation,
        SoftwareFeatureTransform<Definition, OwnBuildModel, TargetDefinition> transform
    );

    class ModelBindingTypeInformation<
        Definition extends HasBuildModel<OwnBuildModel>,
        OwnBuildModel extends BuildModel,
        TargetDefinition extends HasBuildModel<?>
        > {

        public final Class<Definition> definitionType;
        public final Class<OwnBuildModel> ownBuildModelType;
        public final TargetTypeInformation<TargetDefinition> targetType;

        public ModelBindingTypeInformation(
            Class<Definition> definitionType,
            Class<OwnBuildModel> ownBuildModelType,
            TargetTypeInformation<TargetDefinition> targetType
        ) {
            this.definitionType = definitionType;
            this.targetType = targetType;
            this.ownBuildModelType = ownBuildModelType;
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
        Class<OwnBuildModel> ownBuildModel = ModelTypeUtils.getBuildModelClass(definition);
        return new ModelBindingTypeInformation<>(definition, ownBuildModel, new DefinitionTargetTypeInformation<>(targetDefinition));
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
        Class<OwnBuildModel> ownBuildModel = ModelTypeUtils.getBuildModelClass(definition);
        return new ModelBindingTypeInformation<>(definition, ownBuildModel, new BuildModelTargetTypeInformation<>(targetBuildModel));
    }

    // TODO: do not expose this to user code
    List<SoftwareFeatureBinding<?, ?>> build();
}

class ModelTypeUtils {
    static <Definition extends HasBuildModel<OwnBuildModel>, OwnBuildModel extends BuildModel> @Nonnull Class<OwnBuildModel> getBuildModelClass(Class<Definition> definition) {
        @SuppressWarnings("rawtypes")
        TypeParameterInspection<HasBuildModel, BuildModel> inspection = new DefaultTypeParameterInspection<>(HasBuildModel.class, BuildModel.class, BuildModel.NONE.class);
        Class<OwnBuildModel> ownBuildModel = inspection.parameterTypeFor(definition);
        if (ownBuildModel == null) {
            throw new IllegalArgumentException("Cannot determine build model type for " + definition);
        }
        return ownBuildModel;
    }
}
