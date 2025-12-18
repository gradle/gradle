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
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.DefaultPluginModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.classpath.PluginModuleRegistry
import org.gradle.api.internal.classpath.RuntimeApiInfo
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.installation.GradleInstallation
import spock.lang.Specification

class DynamicModulesClassPathProviderTest extends Specification {

    final ModuleRegistry moduleRegistry = new DefaultModuleRegistry(new GradleInstallation(IntegrationTestBuildContext.INSTANCE.gradleHomeDir))
    final ClassPath apiInfoClasspath = moduleRegistry.getModule("gradle-runtime-api-info").getImplementationClasspath()
    final RuntimeApiInfo runtimeApiInfo = RuntimeApiInfo.create(apiInfoClasspath)
    final PluginModuleRegistry pluginModuleRegistry = new DefaultPluginModuleRegistry(moduleRegistry, runtimeApiInfo)

    def "uses plugins and extension plugins to determine gradle extensions classpath"() {
        given:
        def provider = new DynamicModulesClassPathProvider(moduleRegistry, pluginModuleRegistry)

        when:
        def classpath = provider.findClassPath("GRADLE_EXTENSIONS")

        then:
        classpath.asFiles.collect { it.name } == [
            "gradle-workers.jar",
            "gradle-dependency-management.jar",
            "gradle-software-diagnostics.jar",
            "gradle-plugin-use.jar",
            "gradle-instrumentation-declarations.jar",
            "plugin1.jar",
            "plugin2.jar",
            "extension1.jar",
            "extension2.jar"
        ]
    }

    def "removes JAXB from classpath on Java #javaVersion"() {
        when:
        List<String> classpath = findClassPath(javaVersion)

        then:
        !classpath.contains("jaxb-impl-2.3.1.jar")

        where:
        javaVersion << JavaVersion.values().findAll { it < JavaVersion.VERSION_1_9 }
    }

    def "keeps JAXB on classpath on Java #javaVersion"() {
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

}
