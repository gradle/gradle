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

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.features.annotations.ProjectFeature;
import org.gradle.features.annotations.ProjectType;
import org.gradle.features.binding.BuildModel;
import org.gradle.features.binding.Definition;
import org.gradle.features.binding.ProjectFeatureApplicationContext;
import org.gradle.features.binding.ProjectFeatureApplyAction;
import org.gradle.features.binding.SchemaDefinition;
import org.gradle.features.binding.SchemaProjectFeatureApplyAction;
import org.gradle.features.binding.SchemaProjectTypeApplyAction;
import org.gradle.features.binding.TargetTypeInformation;
import org.gradle.internal.inspection.DefaultTypeParameterInspection;
import org.gradle.internal.inspection.TypeParameterInspection;
import org.gradle.util.Path;

/**
 * Builds a {@link ProjectFeatureBindingDeclaration} from a schema apply action class
 * ({@code SchemaProjectTypeApplyAction} or {@code SchemaProjectFeatureApplyAction}) that was applied
 * directly in a settings {@code plugins { }} block. The DSL name comes from the
 * {@link ProjectType}/{@link ProjectFeature} annotation; the definition type (and parent type, for
 * features) is inferred from the apply action's generic type parameters. Bindings produced this way
 * are always unsafe, use a managed definition and {@link BuildModel.None}.
 */
public class SchemaBindingFactory {

    @SuppressWarnings("rawtypes")
    private static final TypeParameterInspection<SchemaProjectTypeApplyAction, SchemaDefinition> TYPE_DEFINITION_INSPECTION =
        new DefaultTypeParameterInspection<>(SchemaProjectTypeApplyAction.class, SchemaDefinition.class, SchemaDefinition.class);

    @SuppressWarnings("rawtypes")
    private static final TypeParameterInspection<SchemaProjectFeatureApplyAction, SchemaDefinition> FEATURE_DEFINITION_INSPECTION =
        new DefaultTypeParameterInspection<>(SchemaProjectFeatureApplyAction.class, SchemaDefinition.class, SchemaDefinition.class);

    @SuppressWarnings("rawtypes")
    private static final TypeParameterInspection<SchemaProjectFeatureApplyAction, Object> FEATURE_PARENT_INSPECTION =
        new DefaultTypeParameterInspection<>(SchemaProjectFeatureApplyAction.class, Object.class, Object.class);

    private SchemaBindingFactory() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ProjectFeatureBindingDeclaration<?, ?> buildBinding(Class<?> schemaApplyActionClass) {
        if (SchemaProjectTypeApplyAction.class.isAssignableFrom(schemaApplyActionClass)) {
            String name = schemaApplyActionClass.getAnnotation(ProjectType.class).name();
            Class definitionType = TYPE_DEFINITION_INSPECTION.parameterTypeFor((Class) schemaApplyActionClass);
            return buildBinding(name, definitionType, new TargetTypeInformation.DefinitionTargetTypeInformation<>(Project.class), typeApplyActionFactory(schemaApplyActionClass));
        } else {
            String name = schemaApplyActionClass.getAnnotation(ProjectFeature.class).name();
            Class definitionType = FEATURE_DEFINITION_INSPECTION.parameterTypeFor((Class) schemaApplyActionClass);
            Class parentType = FEATURE_PARENT_INSPECTION.parameterTypeFor((Class) schemaApplyActionClass, 1);
            return buildBinding(name, definitionType, new TargetTypeInformation.DefinitionTargetTypeInformation<>(parentType), featureApplyActionFactory(schemaApplyActionClass));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ProjectFeatureBindingDeclaration<?, ?> buildBinding(
        String name,
        Class definitionType,
        TargetTypeInformation<?> targetDefinitionType,
        ProjectFeatureApplyActionFactory applyActionFactory
    ) {
        DefaultDeclaredProjectFeatureBindingBuilder builder = new DefaultDeclaredProjectFeatureBindingBuilder(
            definitionType,
            BuildModel.None.class,
            targetDefinitionType,
            Path.path(name),
            applyActionFactory
        );
        builder.withUnsafeDefinition();
        builder.withUnsafeApplyAction();
        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ProjectFeatureApplyActionFactory typeApplyActionFactory(Class<?> actionClass) {
        // Must use anonymous classes (not lambdas) for configuration cache compatibility.
        return new ProjectFeatureApplyActionFactory() {
            @Override
            public ProjectFeatureApplyAction create(ObjectFactory objectFactory) {
                return new ProjectFeatureApplyAction() {
                    @Override
                    public void apply(ProjectFeatureApplicationContext context, Definition definition, BuildModel buildModel, Object parentDefinition) {
                        SchemaProjectTypeApplyAction action = (SchemaProjectTypeApplyAction) objectFactory.newInstance(actionClass);
                        action.apply(context, (SchemaDefinition) definition, (BuildModel.None) buildModel);
                    }
                };
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ProjectFeatureApplyActionFactory featureApplyActionFactory(Class<?> actionClass) {
        // A SchemaProjectFeatureApplyAction is already a ProjectFeatureApplyAction (its 4-arg apply
        // default-delegates to the simplified apply), so the instance can be used directly.
        return new ProjectFeatureApplyActionFactory() {
            @Override
            public ProjectFeatureApplyAction create(ObjectFactory objectFactory) {
                return (ProjectFeatureApplyAction) objectFactory.newInstance(actionClass);
            }
        };
    }
}
