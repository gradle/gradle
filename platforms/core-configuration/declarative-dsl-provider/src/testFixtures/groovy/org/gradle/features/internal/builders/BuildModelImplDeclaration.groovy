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
 * Describes a concrete build model implementation class that a plugin provides for
 * NDOC elements that implement {@code Definition<BuildModel>}.
 *
 * <p>The generated class is a static abstract class inside the plugin that implements
 * the build model interface from the definition's nested NDOC element. It is registered
 * via {@code .withNestedBuildModelImplementationType()} in the plugin's binding chain.</p>
 *
 * @see PluginClassBuilder#providesBuildModelImpl
 */
class BuildModelImplDeclaration {
    /** The simple class name of the implementation (e.g. "DefaultSourceModel"). */
    String implClassName

    /** The interface this implementation fulfills (e.g. "Source.SourceModel"). */
    String interfaceName

    /** Properties declared on this implementation. */
    List<PropertyDeclaration> properties = []

    /** Adds a property to this build model implementation. */
    void property(String name, Class type) {
        properties.add(PropertyDeclaration.property(name, type))
    }
}
