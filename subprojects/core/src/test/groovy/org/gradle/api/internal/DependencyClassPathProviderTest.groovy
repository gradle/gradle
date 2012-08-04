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
import spock.lang.Specification
import org.gradle.api.internal.classpath.PluginModuleRegistry
import org.gradle.internal.classpath.DefaultClassPath

class DependencyClassPathProviderTest extends Specification {
    final ModuleRegistry moduleRegistry = Mock()
    final PluginModuleRegistry pluginModuleRegistry = Mock()
    final DependencyClassPathProvider provider = new DependencyClassPathProvider(moduleRegistry, pluginModuleRegistry)

    def "uses modules to determine gradle API classpath"() {
        when:
        def classpath = provider.findClassPath("GRADLE_API")

        then:
        classpath.asFiles.collect{it.name} == ["gradle-core-runtime", "gradle-cli-runtime", "gradle-core-impl-runtime", "gradle-tooling-api-impl", "plugin1-runtime", "plugin2-runtime"]

        and:
        1 * moduleRegistry.getModule("gradle-core") >> module("gradle-core", module("gradle-cli"))
        1 * moduleRegistry.getModule("gradle-core-impl") >> module("gradle-core-impl")
        1 * moduleRegistry.getModule("gradle-tooling-api") >> module("gradle-tooling-api")
        1 * pluginModuleRegistry.getPluginModules() >> ([module("plugin1"), module("plugin2")] as LinkedHashSet)
    }

    def module(String name, Module ... requiredModules) {
        Module module = Mock()
        _ * module.classpath >> new DefaultClassPath(new File("$name-runtime"))
        _ * module.implementationClasspath >> new DefaultClassPath(new File("$name-impl"))
        _ * module.allRequiredModules >> (([module] + (requiredModules as List)) as LinkedHashSet)
        return module
    }
}
