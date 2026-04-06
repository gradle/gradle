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

/**
 * Describes an additional binding for plugins that bind multiple project types or features.
 *
 * <p>For project type plugins that expose multiple types, set {@link #definition} and {@link #name}
 * to specify the additional type's definition and name.</p>
 *
 * <p>For project feature plugins that bind to multiple parent types, set {@link #bindingTypeClassName}
 * to specify the additional parent type class name.</p>
 *
 * @see PluginClassBuilder#bindsType
 * @see PluginClassBuilder#bindsFeatureTo
 */
class BindingDeclaration {
    /** The definition builder for the additional type binding. Used by project type plugins. */
    DefinitionBuilder definition

    /** The name of the additional project type. Used by project type plugins. */
    String name

    /** The fully qualified class name of the additional binding target. Used by project feature plugins. */
    String bindingTypeClassName

    /** The binding method name (e.g. "bindProjectFeatureToDefinition" or "bindProjectFeatureToBuildModel"). Used by project feature plugins. */
    String bindingMethodName = "bindProjectFeatureToDefinition"
}
