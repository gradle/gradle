/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal

import org.gradle.api.internal.classpath.Module
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.classpath.PluginModuleRegistry
import org.gradle.internal.classpath.DefaultClassPath
import spock.lang.Specification

class DynamicModulesClassPathProviderTest extends Specification {

    def moduleRegistry = Mock(ModuleRegistry)
    def pluginModuleRegistry = Mock(PluginModuleRegistry)
    def provider = new DynamicModulesClassPathProvider(moduleRegistry, pluginModuleRegistry)

    def "uses plugins and extension plugins to determine gradle extensions classpath"() {
        when:
        def classpath = provider.findClassPath("GRADLE_EXTENSIONS")

        then:
        classpath.asFiles.collect { it.name } == ["gradle-workers-runtime",
                                                  "gradle-dependency-management-runtime",
                                                  "gradle-plugin-use-runtime",
                                                  "plugin1-runtime", "plugin2-runtime",
                                                  "extension1-runtime", "extension2-runtime"]

        and:
        1 * moduleRegistry.getModule("gradle-core") >> module("gradle-core", module("gradle-cli"))
        1 * moduleRegistry.getModule("gradle-workers") >> module("gradle-workers")
        1 * moduleRegistry.getModule("gradle-dependency-management") >> module("gradle-dependency-management")
        1 * moduleRegistry.getModule("gradle-plugin-use") >> module("gradle-plugin-use")
        1 * pluginModuleRegistry.getApiModules() >> ([module("plugin1"), module("plugin2")] as LinkedHashSet)
        1 * pluginModuleRegistry.getImplementationModules() >> ([module("extension1"), module("extension2")] as LinkedHashSet)
    }

    def module(String name, Module... requiredModules) {
        def module = Mock(Module)
        _ * module.classpath >> new DefaultClassPath(new File("$name-runtime"))
        _ * module.implementationClasspath >> new DefaultClassPath(new File("$name-runtime"))
        _ * module.allRequiredModules >> (([module] + (requiredModules as List)) as LinkedHashSet)
        return module
    }
}
