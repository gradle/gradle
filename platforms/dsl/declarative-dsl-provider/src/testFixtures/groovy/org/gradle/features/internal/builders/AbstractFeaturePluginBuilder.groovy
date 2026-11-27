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

import org.gradle.api.provider.ProviderFactory
import org.gradle.features.binding.Definition
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.registration.TaskRegistrar

/**
 * Base class for plugin builders that emit a {@code @BindsProjectFeature}-annotated plugin.
 *
 * <p>Owns the binding list (with the primary binding at index 0) and the rendering helpers
 * shared by every feature-plugin shape — service injection, parent-type resolution, and the
 * mapping/print delegators that produce the apply-action body.</p>
 */
abstract class AbstractFeaturePluginBuilder extends AbstractPluginBuilder {

    /** Bindings for this feature plugin. The first entry is the primary binding. */
    List<BindingDeclaration> bindings = []

    /** Returns the primary binding's name. */
    String getName() { bindings[0].name }

    /** Returns the primary binding's definition. */
    DefinitionBuilder getPrimaryDefinition() { bindings[0].definition }

    /** Returns the primary binding's target type class name. */
    String getBindingTypeClassName() { bindings[0].bindingTypeClassName }

    /** Returns the primary binding's method name. */
    String getBindingMethodName() { bindings[0].bindingMethodName }

    /** Returns the parent generic argument used by the apply-action's interface. */
    protected String getParentTypeForApplyAction() {
        if (bindingMethodName == "bindProjectFeatureToBuildModel") {
            return "${Definition.class.name}<${bindingTypeClassName}>"
        }
        return bindingTypeClassName
    }

    /** Renders the standard feature service injections (or the user-configured services). */
    protected String generateJavaFeatureApplyActionServices() {
        if (!applyActionDeclaration.injectedServices.isEmpty()) {
            return generateCustomServices(applyActionDeclaration.injectedServices, true)
        }
        return """
@javax.inject.Inject
abstract protected ${TaskRegistrar.class.name} getTaskRegistrar();

@javax.inject.Inject
abstract protected ${ProjectFeatureLayout.class.name} getProjectFeatureLayout();

@javax.inject.Inject
abstract protected ${ProviderFactory.class.name} getProviderFactory();
"""
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
