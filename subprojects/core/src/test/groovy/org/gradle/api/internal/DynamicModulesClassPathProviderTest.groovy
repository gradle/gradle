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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.classpath.Module
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.classpath.PluginModuleRegistry
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import spock.lang.Specification

class DynamicModulesClassPathProviderTest extends Specification {

    def moduleRegistry = Mock(ModuleRegistry) {
        getModule(_) >> { args -> module(args[0]) }
    }
    def pluginModuleRegistry = Mock(PluginModuleRegistry) {
        getImplementationModules() >> []
    }

    def "uses plugins and extension plugins to determine gradle extensions classpath"() {
        given:
        def provider = new DynamicModulesClassPathProvider(moduleRegistry, pluginModuleRegistry)

        when:
        def classpath = provider.findClassPath("GRADLE_EXTENSIONS")

        then:
        classpath.asFiles.collect { it.name } == [
            "gradle-workers.jar",
            "gradle-dependency-management.jar",
            "gradle-plugin-use.jar",
            "gradle-instrumentation-declarations.jar",
            "plugin1.jar", "plugin2.jar",
            "extension1.jar", "extension2.jar"
        ]

        and:
        1 * moduleRegistry.getModule("gradle-core") >> module("gradle-core", module("gradle-cli"))
        1 * moduleRegistry.getModule("gradle-workers") >> module("gradle-workers")
        1 * moduleRegistry.getModule("gradle-dependency-management") >> module("gradle-dependency-management")
        1 * moduleRegistry.getModule("gradle-plugin-use") >> module("gradle-plugin-use")
        1 * pluginModuleRegistry.getApiModules() >> ([module("plugin1"), module("plugin2")] as LinkedHashSet)
        1 * pluginModuleRegistry.getImplementationModules() >> ([module("extension1"), module("extension2")] as LinkedHashSet)
    }

    def "removes JAXB from classpath on Java #javaVersion"() {
        given:
        pluginModuleRegistry.getApiModules() >> ([module("gradle-resources-s3", ["jaxb-impl-2.3.1.jar"])] as Set)

        when:
        List<String> classpath = findClassPath(javaVersion)

        then:
        !classpath.contains("jaxb-impl-2.3.1.jar")

        where:
        javaVersion << JavaVersion.values().findAll { it < JavaVersion.VERSION_1_9 }
    }

    def "keeps JAXB on classpath on Java #javaVersion"() {
        given:
        pluginModuleRegistry.getApiModules() >> ([module("gradle-resources-s3", ["jaxb-impl-2.3.1.jar"])] as Set)

        when:
        List<String> classpath = findClassPath(javaVersion)

        then:
        classpath.contains("jaxb-impl-2.3.1.jar")

        where:
        javaVersion << JavaVersion.values().findAll { it >= JavaVersion.VERSION_1_9 }
    }

    private List<String> findClassPath(JavaVersion javaVersion) {
        def provider = new DynamicModulesClassPathProvider(moduleRegistry, pluginModuleRegistry, javaVersion)
        provider.findClassPath("GRADLE_EXTENSIONS").asFiles.collect { it.name }
    }

    def module(String name, List<String> additionalClasspathEntries = [], Module... requiredModules) {
        def module = Stub(Module)
        _ * module.classpath >> DefaultClassPath.of([new File("${name}.jar")] + additionalClasspathEntries.collect { new File(it) })
        _ * module.implementationClasspath >> DefaultClassPath.of(new File("${name}.jar"))
        _ * module.allRequiredModules >> (([module] + (requiredModules as List)) as LinkedHashSet)
        _ * module.allRequiredModulesClasspath >> module.allRequiredModules.collect { it.classpath }.inject(ClassPath.EMPTY) { r, i -> r + i }
        return module
    }
}
