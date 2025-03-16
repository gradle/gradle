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
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import spock.lang.Specification

class DependencyClassPathProviderTest extends Specification {
    final ModuleRegistry moduleRegistry = Mock()
    final PluginModuleRegistry pluginModuleRegistry = Mock()
    final DependencyClassPathProvider provider = new DependencyClassPathProvider(moduleRegistry, pluginModuleRegistry)

    def "uses modules to determine gradle API classpath"() {
        when:
        def classpath = provider.findClassPath("GRADLE_API")

        then:
        classpath.asFiles.collect{it.name} == [
            "gradle-worker-main-runtime",
            "gradle-launcher-runtime",
            "gradle-cli-runtime",
            "gradle-workers-runtime",
            "gradle-dependency-management-runtime",
            "gradle-plugin-use-runtime",
            "gradle-tooling-api-builders-runtime",
            "gradle-configuration-cache-runtime",
            "gradle-unit-test-fixtures-runtime",
            "plugin1-runtime",
            "plugin2-runtime"
        ]

        and:
        1 * moduleRegistry.getModule("gradle-worker-main") >> module("gradle-worker-main")
        1 * moduleRegistry.getModule("gradle-launcher") >> module("gradle-launcher", module("gradle-cli"))
        1 * moduleRegistry.getModule("gradle-workers") >> module("gradle-workers")
        1 * moduleRegistry.getModule("gradle-dependency-management") >> module("gradle-dependency-management")
        1 * moduleRegistry.getModule("gradle-plugin-use") >> module("gradle-plugin-use")
        1 * moduleRegistry.getModule("gradle-tooling-api-builders") >> module("gradle-tooling-api-builders")
        1 * moduleRegistry.getModule("gradle-configuration-cache") >> module("gradle-configuration-cache")
        1 * moduleRegistry.getModule("gradle-unit-test-fixtures") >> module("gradle-unit-test-fixtures")
        1 * pluginModuleRegistry.getApiModules() >> ([module("plugin1"), module("plugin2")] as LinkedHashSet)
    }

    def "uses modules to determine Gradle test-kit classpath"() {
        when:
        def classpath = provider.findClassPath("GRADLE_TEST_KIT")

        then:
        classpath.asFiles.collect { it.name } == ["gradle-test-kit-runtime"]

        and:
        1 * moduleRegistry.getModule("gradle-test-kit") >> module("gradle-test-kit")
        0 * pluginModuleRegistry.getApiModules()
    }

    def module(String name, Module... requiredModules) {
        Module module = Mock()
        _ * module.classpath >> DefaultClassPath.of(new File("$name-runtime"))
        _ * module.implementationClasspath >> DefaultClassPath.of(new File("$name-runtime"))
        _ * module.allRequiredModules >> (([module] + (requiredModules as List)) as LinkedHashSet)
        _ * module.allRequiredModulesClasspath >> module.allRequiredModules.collect { it.classpath }.inject(ClassPath.EMPTY) { r, i -> r + i }
        return module
    }
}
