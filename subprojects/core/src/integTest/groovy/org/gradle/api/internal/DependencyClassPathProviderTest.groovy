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

import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.DefaultPluginModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.classpath.PluginModuleRegistry
import org.gradle.api.internal.classpath.RuntimeApiInfo
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.installation.GradleInstallation
import spock.lang.Specification

class DependencyClassPathProviderTest extends Specification {

    final ModuleRegistry moduleRegistry = new DefaultModuleRegistry(new GradleInstallation(IntegrationTestBuildContext.INSTANCE.gradleHomeDir))
    final ClassPath apiInfoClasspath = moduleRegistry.getModule("gradle-runtime-api-info").getImplementationClasspath()
    final RuntimeApiInfo runtimeApiInfo = RuntimeApiInfo.create(apiInfoClasspath)
    final PluginModuleRegistry pluginModuleRegistry = new DefaultPluginModuleRegistry(moduleRegistry, runtimeApiInfo)

    final DependencyClassPathProvider provider = new DependencyClassPathProvider(moduleRegistry, pluginModuleRegistry)

    def "uses modules to determine gradle API classpath"() {
        when:
        def classpath = provider.findClassPath("GRADLE_API")

        then:
        classpath.asFiles*.name == [
            "gradle-worker-main-runtime",
            "gradle-launcher-runtime",
            "gradle-cli-runtime",
            "gradle-workers-runtime",
            "gradle-dependency-management-runtime",
            "gradle-plugin-use-runtime",
            "gradle-tooling-api-builders-runtime",
            "gradle-configuration-cache-runtime",
            "gradle-isolated-action-services-runtime",
            "gradle-unit-test-fixtures-runtime",
            "plugin1-runtime",
            "plugin2-runtime"
        ]
    }

    def "uses modules to determine Gradle test-kit classpath"() {
        when:
        def classpath = provider.findClassPath("GRADLE_TEST_KIT")

        then:
        classpath.asFiles.collect { it.name } == ["gradle-test-kit-runtime"]
    }

}
