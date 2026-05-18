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

import org.gradle.features.registration.TaskRegistrar

/**
 * Base class for plugin builders that emit a {@code @BindsProjectType}-annotated plugin.
 *
 * <p>Owns the binding list (with the primary binding at index 0), the build-model implementation
 * declarations, and the rendering helpers shared by every project-type plugin shape — service
 * injection, build-model implementation classes, and the mapping/print delegators.</p>
 */
abstract class AbstractTypePluginBuilder extends AbstractPluginBuilder {

    /** Bindings for this project-type plugin. The first entry is the primary binding. */
    List<BindingDeclaration> bindings = []

    /** Build model implementation classes provided by this plugin (for NDOC element models). */
    List<BuildModelImplDeclaration> buildModelImplementations = []

    /** Returns the primary binding's name. */
    String getName() { bindings[0].name }

    /** Returns the primary binding's definition. */
    DefinitionBuilder getPrimaryDefinition() { bindings[0].definition }

    /** Renders the standard project-type service injections (or the user-configured services). */
    protected String generateJavaTypeApplyActionServices() {
        if (!applyActionDeclaration.injectedServices.isEmpty()) {
            return generateCustomServices(applyActionDeclaration.injectedServices, false)
        }
        return """
@javax.inject.Inject
abstract protected ${TaskRegistrar.class.name} getTaskRegistrar();
"""
    }

    /** Renders inner classes that implement NDOC build-model interfaces declared on the primary definition. */
    protected String generateBuildModelImplClasses() {
        return buildModelImplementations.collect { implementation ->
            def propertyDeclarations = implementation.properties.collect { property ->
                "public abstract ${AbstractTypePluginBuilder.getPropertyReturnType(property)} get${JavaSources.capitalize(property.name)}();"
            }.join("\n")
            """
public static abstract class ${implementation.implClassName} implements ${primaryDefinition.className}.${implementation.interfaceName} {
${propertyDeclarations}
}
"""
        }.join("\n")
    }

    static String getPropertyReturnType(PropertyDeclaration property) {
        if (property.kind == PropertyKind.LIST_PROPERTY) {
            return "org.gradle.api.provider.ListProperty<${property.type.simpleName}>"
        }
        return "org.gradle.api.provider.Property<${property.type.simpleName}>"
    }

    /** Returns the build model mapping code for the current language. */
    protected String buildModelMappingForLanguage() {
        return primaryDefinition.getBuildModelMapping(language)
    }

    /** Returns the definition property display code for the current language. */
    protected String displayDefinitionValuesForLanguage() {
        return primaryDefinition.displayDefinitionPropertyValues(language)
    }

    /** Returns the model property display code for the current language. */
    protected String displayModelValuesForLanguage() {
        return primaryDefinition.displayModelPropertyValues(language)
    }
}
