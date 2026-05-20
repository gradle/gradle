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

import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectTypeApplyAction
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.features.registration.TaskRegistrar

/**
 * Generates a {@code @BindsProjectType}-annotated plugin with a single project-type binding,
 * using the standard class-based binding style for both Java and Kotlin.
 */
class SingleTypePluginBuilder extends AbstractTypePluginBuilder {

    @Override
    protected String renderJava() {
        def modifiers = maybeDeclareBindingModifiers()
        def implType = primaryDefinition.implementationClassName
            ? ".withUnsafeDefinitionImplementationType(${primaryDefinition.implementationClassName}.class)"
            : ""
        def ndocImplTypes = buildModelImplementations.collect {
            ".withNestedBuildModelImplementationType(${primaryDefinition.className}.${it.interfaceName}.class, ${it.implClassName}.class)"
        }.join("")

        return """
            package ${packageName};

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Nested;
            import ${ProjectTypeApplyAction.class.name};
            import ${ProjectFeatureApplicationContext.class.name};
            import ${ProjectTypeBinding.class.name};
            import ${BindsProjectType.class.name};
            import ${ProjectTypeBindingBuilder.class.name};
            import javax.inject.Inject;

            @${BindsProjectType.class.simpleName}(${pluginClassName}.Binding.class)
            abstract public class ${pluginClassName} implements Plugin<Project> {

                static class Binding implements ${ProjectTypeBinding.class.simpleName} {
                    public void bind(${ProjectTypeBindingBuilder.class.simpleName} builder) {
                        builder.bindProjectType("${name}", ${primaryDefinition.className}.class, ${pluginClassName}.ApplyAction.class)
                        ${implType}${ndocImplTypes}${modifiers};
                    }
                }

                static abstract class ApplyAction implements ${ProjectTypeApplyAction.class.name}<${primaryDefinition.className}, ${primaryDefinition.fullyQualifiedBuildModelClassName}> {
                    @javax.inject.Inject
                    public ApplyAction() { }

                    ${generateJavaTypeApplyActionServices()}

                    @Override
                    public void apply(${ProjectFeatureApplicationContext.class.name} context, ${primaryDefinition.className} definition, ${primaryDefinition.fullyQualifiedBuildModelClassName} model) {
                        System.out.println("Binding " + ${primaryDefinition.className}.class.getSimpleName());

                        ${generateTypeApplyActionBody()}
                    }
                }

                ${generateBuildModelImplClasses()}

                @Override
                public void apply(Project target) {
                    System.out.println("Applying " + getClass().getSimpleName());
                }
            }
        """
    }

    @Override
    protected String renderKotlin() {
        def modifiers = maybeDeclareBindingModifiers()
        def implType = primaryDefinition.implementationClassName
            ? ".withUnsafeDefinitionImplementationType(${primaryDefinition.implementationClassName}::class.java)"
            : ""

        return """
            package ${packageName}

            import org.gradle.api.Task
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.provider.ListProperty
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Nested
            import ${ProjectTypeBinding.class.name}
            import ${BindsProjectType.class.name}
            import ${ProjectTypeBindingBuilder.class.name}
            import ${ProjectTypeApplyAction.class.name}
            import javax.inject.Inject

            @${BindsProjectType.class.simpleName}(${pluginClassName}.Binding::class)
            class ${pluginClassName} : Plugin<Project> {

                class Binding : ${ProjectTypeBinding.class.simpleName} {
                    override fun bind(builder: ${ProjectTypeBindingBuilder.class.simpleName}) {
                        builder.bindProjectType("${name}", ${primaryDefinition.className}::class.java, ${pluginClassName}.ApplyAction::class.java)
                        ${implType}${modifiers}
                    }
                }

                abstract class ApplyAction @Inject constructor() : ${ProjectTypeApplyAction.class.simpleName}<${primaryDefinition.className}, ${primaryDefinition.fullyQualifiedBuildModelClassName}> {

                    @get:javax.inject.Inject
                    abstract val taskRegistrar: ${TaskRegistrar.class.name}

                    override fun apply(context: ${ProjectFeatureApplicationContext.class.name}, definition: ${primaryDefinition.className}, model: ${primaryDefinition.fullyQualifiedBuildModelClassName}) {
                        println("Binding " + ${primaryDefinition.className}::class.simpleName)

                        ${buildModelMappingForLanguage()}

                        taskRegistrar.register("print${primaryDefinition.className}Configuration") { task: Task ->
                            task.doLast { _: Task ->
                                ${displayDefinitionValuesForLanguage()}
                                ${displayModelValuesForLanguage()}
                            }
                        }
                    }
                }

                override fun apply(project: Project) {
                    println("Applying " + this::class.java.simpleName)
                }
            }
        """
    }

    /** Renders the apply-action body, choosing between standard and eager-read variants. */
    protected String generateTypeApplyActionBody() {
        if (applyActionDeclaration.readsValuesEagerly) {
            return generateEagerReadApplyBody()
        }
        return """
${buildModelMappingForLanguage()}

getTaskRegistrar().register("print${primaryDefinition.className}Configuration", DefaultTask.class, task -> {
task.doLast("print restricted extension content", t -> {
${displayDefinitionValuesForLanguage()}
${displayModelValuesForLanguage()}
});
});

${customApplyActionCode}
"""
    }

    private String generateEagerReadApplyBody() {
        def eagerReads = []
        def printStatements = []
        primaryDefinition.properties.each { property ->
            def varName = "${property.name}AtApplyTime"
            eagerReads << "String ${varName} = definition.get${JavaSources.capitalize(property.name)}().get();"
            printStatements << """System.out.println("apply time ${property.name} = " + ${varName});"""
        }
        primaryDefinition.nestedTypes.findAll { it.kind != NestedKind.NDOC }.each { nestedType ->
            nestedType.properties.each { property ->
                def varName = "${nestedType.name}${JavaSources.capitalize(property.name)}AtApplyTime"
                eagerReads << "String ${varName} = definition.get${JavaSources.capitalize(nestedType.name)}().get${JavaSources.capitalize(property.name)}().get();"
                printStatements << """System.out.println("apply time ${nestedType.name}.${property.name} = " + ${varName});"""
            }
        }

        return """
// Eagerly read values at apply time.
// These reads throw MissingValueException if the definition
// hasn't been configured yet - that is what this fixture tests against.
${eagerReads.join("\n")}

${buildModelMappingForLanguage()}

getTaskRegistrar().register("printApplyTimeValues", DefaultTask.class, task -> {
task.doLast("print", t -> {
${printStatements.join("\n")}
});
});
"""
    }
}
