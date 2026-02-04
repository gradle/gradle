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
import org.gradle.api.internal.classpath.Module
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

    def "contains specific top level modules and contains plugin modules"() {
        when:
        def classpath = provider.findClassPath("GRADLE_API")

        then:
        // We verify here we have _at least_ these top-level modules in the classpath
        // In practice, we have the entire transitive closure of these module's runtime classpath
        // However, verifying that classpath here would likely be very tedious as it may change
        // often.
        [
            "gradle-worker-main",
            "gradle-launcher",
            "gradle-workers",
            "gradle-dependency-management",
            "gradle-plugin-use",
            "gradle-tooling-api-builders",
            "gradle-configuration-cache",
            "gradle-isolated-action-services",
            "gradle-unit-test-fixtures"
        ].each {
            assertContainsModule(classpath, moduleRegistry.getModule(it))
        }

        and:
        pluginModuleRegistry.apiModules.each {
            assertContainsModule(classpath, it)
        }
    }

    def "uses modules to determine Gradle test-kit classpath"() {
        when:
        def classpath = provider.findClassPath("GRADLE_TEST_KIT")

        then:
        // In practice, the classpath contains the entire transitive closure of
        // test kit's runtime classpath, but we only check the top-level here
        // to prevent this test from becoming unmaintainable
        assertContainsModule(classpath, moduleRegistry.getModule("gradle-test-kit"))
    }

    void assertContainsModule(ClassPath classpath, Module module) {
        module.implementationClasspath.asFiles.each {
            assert classpath.asFiles.contains(it)
        }
    }

}
