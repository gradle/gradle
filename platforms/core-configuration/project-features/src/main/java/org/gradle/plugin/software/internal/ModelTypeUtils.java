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

package org.gradle.plugin.software.internal;

import org.gradle.api.internal.plugins.BuildModel;
import org.gradle.api.internal.plugins.Definition;
import org.gradle.internal.inspection.DefaultTypeParameterInspection;
import org.gradle.internal.inspection.TypeParameterInspection;
import org.jspecify.annotations.NonNull;

public class ModelTypeUtils {
    public static <OwnDefinition extends Definition<OwnBuildModel>, OwnBuildModel extends BuildModel> @NonNull Class<OwnBuildModel> getBuildModelClass(Class<OwnDefinition> definition) {
        @SuppressWarnings("rawtypes")
        TypeParameterInspection<Definition, BuildModel> inspection = new DefaultTypeParameterInspection<>(Definition.class, BuildModel.class, BuildModel.NONE.class);
        Class<OwnBuildModel> ownBuildModel = inspection.parameterTypeFor(definition);
        if (ownBuildModel == null) {
            throw new IllegalArgumentException("Cannot determine build model type for " + definition);
        }
        return ownBuildModel;
    }
}
