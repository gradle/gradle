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

package org.gradle.features.internal.builders

import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder

/**
 * Generates a {@code @BindsProjectFeature}-annotated plugin that binds the same feature to
 * multiple parent target types. Each target gets its own concrete {@code ApplyAction} subclass
 * that extends a common generic {@code BaseApplyAction<P>}.
 *
 * <p>Kotlin rendering is intentionally unsupported — no fixture currently exercises a multi-target
 * feature in Kotlin, so the {@code BaseApplyAction<P>} shape has not been ported.</p>
 */
class MultiTargetFeaturePluginBuilder extends AbstractFeaturePluginBuilder {

    @Override
    protected String renderJava() {
        def modifiers = maybeDeclareBindingModifiers()
        def buildModelType = primaryDefinition.buildModelFullPublicClassName

        def allTargets = bindings.collect { it.bindingTypeClassName }.findAll { it != null }
        def bindCalls = allTargets.withIndex().collect { target, idx ->
            def simpleTarget = target.tokenize('.').last()
            """builder.${bindingMethodName}(
                            "${name}",
                            ${primaryDefinition.className}.class,
                            ${target}.class,
                            ${pluginClassName}.${simpleTarget}ApplyAction.class
                        )${modifiers};"""
        }.join("\n                        ")

        def concreteActions = allTargets.withIndex().collect { target, idx ->
            def simpleTarget = target.tokenize('.').last()
            def parentType = (bindingMethodName == "bindProjectFeatureToBuildModel")
                ? "${Definition.class.name}<${target}>"
                : target
            """
                static abstract class ${simpleTarget}ApplyAction extends BaseApplyAction<${parentType}> {
                    @javax.inject.Inject public ${simpleTarget}ApplyAction() { }
                    @Override protected String getTaskName() { return "print${primaryDefinition.className}${idx + 1}Configuration"; }
                }
            """
        }.join("\n")

        return """
            package ${packageName};

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import ${BindsProjectFeature.class.name};
            import ${ProjectFeatureBindingBuilder.class.name};
            import static ${ProjectFeatureBindingBuilder.class.name}.bindingToTargetDefinition;
            import ${ProjectFeatureBinding.class.name};
            import ${ProjectFeatureApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import ${Definition.class.name};

            @${BindsProjectFeature.class.simpleName}(${pluginClassName}.Binding.class)
            public class ${pluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectFeatureBinding.class.simpleName} {
                    @Override public void bind(${ProjectFeatureBindingBuilder.class.simpleName} builder) {
                        ${bindCalls}
                    }
                }

                static abstract class BaseApplyAction<P extends ${Definition.class.name}<?>> implements ${ProjectFeatureApplyAction.class.name}<${primaryDefinition.className}, ${buildModelType}, P> {
                    @javax.inject.Inject public BaseApplyAction() { }

                    ${generateJavaFeatureApplyActionServices()}

                    abstract protected String getTaskName();

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${primaryDefinition.className} definition, ${buildModelType} model, P parent) {
                        System.out.println("Binding ${primaryDefinition.className}");
                        System.out.println("${name} model class: " + model.getClass().getSimpleName());

                        ${buildModelMappingForLanguage()}

                        getTaskRegistrar().register(getTaskName(), task -> {
                            task.doLast(t -> {
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
                            });
                        });
                    }
                }

                ${concreteActions}

                @Override
                public void apply(Project project) {
                }
            }
        """
    }

    @Override
    protected String renderKotlin() {
        throw new UnsupportedOperationException(
            "Multi-target project-feature plugin Kotlin rendering is not supported. " +
            "No existing fixture exercises this combination."
        )
    }
}
