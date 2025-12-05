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
package org.gradle.api.internal.classpath

import org.gradle.internal.installation.GradleInstallation
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.GUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultModuleRegistryTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    TestFile distDir = tmpDir.createDir("dist")
    TestFile libDir = distDir.file("lib")
    TestFile pluginsDir = libDir.file("plugins")

    def "locates module using jar in distribution image"() {
        given:
        TestFile libDep = createExternalModule("dep", libDir)
        TestFile pluginsDep = createExternalModule("dep2", pluginsDir)
        TestFile someModule = createModule("some-module", properties(runtime: "${libDep.name},${pluginsDep.name}", projects: ''), libDir)

        when:
        def module = registry.getModule("gradle-some-module")

        then:
        module.implementationClasspath.asFiles == [someModule]
        module.runtimeClasspath.asFiles == [libDep, pluginsDep]
        module.classpath.asFiles == [someModule, libDep, pluginsDep]
    }

    def "handles empty classpaths in manifest"() {
        given:
        createModule("foo", properties())

        when:
        def module = registry.getModule("gradle-foo")

        then:
        module.runtimeClasspath.empty
        module.requiredModules.empty
    }

    def "modules can depend on other modules"() {
        given:
        createModule("lib-module", properties(), libDir)
        createModule("plugin-module", properties(), pluginsDir)
        createModule("root-lib-module", properties(projects: 'gradle-lib-module,gradle-plugin-module'), libDir)
        createModule("root-plugins-module", properties(projects: 'gradle-lib-module,gradle-plugin-module'), pluginsDir)

        when:
        def libModule = registry.getModule("gradle-root-lib-module")
        def pluginsModule = registry.getModule("gradle-root-plugins-module")

        then:
        libModule.requiredModules as List == [registry.getModule("gradle-lib-module"), registry.getModule("gradle-plugin-module")]
        pluginsModule.requiredModules as List == [registry.getModule("gradle-lib-module"), registry.getModule("gradle-plugin-module")]
    }

    def "builds transitive closure of required modules"() {
        given:
        createModule("module-1", properties(projects: 'gradle-module-2'))
        createModule("module-2", properties(projects: 'gradle-module-3'))
        createModule("module-3", properties(projects: ''))

        when:
        def module = registry.getModule("gradle-module-1")

        then:
        module.allRequiredModules as List == [module, registry.getModule("gradle-module-2"), registry.getModule("gradle-module-3")]
    }

    def "supports cycles between modules"() {
        given:
        createModule("module-1", properties(projects: 'gradle-module-2'))
        createModule("module-2", properties(projects: 'gradle-module-1'))

        when:
        def module1 = registry.getModule("gradle-module-1")
        def module2 = registry.getModule("gradle-module-2")

        then:
        module1.allRequiredModules as List == [module1, module2]
        module2.allRequiredModules as List == [module2, module1]
    }

    def "supports cycles between optional modules"() {
        given:
        createModule("module-1", properties(optional: 'gradle-optional-module,gradle-module-2'))
        createModule("module-2", properties(optional: 'gradle-module-1'))

        when:
        def module1 = registry.getModule("gradle-module-1")
        def module2 = registry.getModule("gradle-module-2")

        then:
        module1.allRequiredModules as List == [module1, module2]
        module2.allRequiredModules as List == [module2, module1]
    }

    def "fails when distribution does not contain module"() {
        when:
        registry.getModule("gradle-other-module")

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot locate JAR for module 'gradle-other-module' in distribution directory '$distDir'."
    }

    def "fails when external module cannot be found"() {
        when:
        registry.getExternalModule("unknown")

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot locate JAR for module 'unknown' in distribution directory '$distDir'."
    }

    def "ignores jars which have the same prefix as an external module"() {
        given:
        TestFile libDep = createExternalModule("dep", libDir)
        createExternalModule("dep-extra", libDir)

        when:
        def module = registry.getExternalModule("dep")

        then:
        module.implementationClasspath.asFiles == [libDep]
        module.runtimeClasspath.empty
    }

    def "also looks in subdirectories of plugins directory when searching for external modules"() {
        when:
        def sonarDependency = createExternalModule("sonar-dependency", pluginsDir.file("sonar"))

        then:
        def module = registry.getExternalModule("sonar-dependency")
        module.implementationClasspath.asFiles == [sonarDependency]
        module.runtimeClasspath.empty
    }

    ModuleRegistry cachedRegistry
    ModuleRegistry getRegistry() {
        if (cachedRegistry == null) {
            cachedRegistry = new DefaultModuleRegistry(new GradleInstallation(distDir))
        }
        cachedRegistry
    }

    TestFile createModule(String simpleName, Properties properties, TestFile destinationDir = libDir) {
        TestFile moduleDir = tmpDir.createDir(simpleName)
        GUtil.saveProperties(properties, moduleDir.file("gradle-$simpleName-classpath.properties"))

        TestFile jarFile = destinationDir.file("gradle-$simpleName-1.jar")
        moduleDir.zipTo(jarFile)
        jarFile
    }

    TestFile createExternalModule(String moduleName, TestFile destinationDir) {
        TestFile moduleDir = tmpDir.createDir(moduleName)

        TestFile jarFile = destinationDir.file("$moduleName-1.jar")
        moduleDir.zipTo(jarFile)
        jarFile
    }

    private static Properties properties(Map<String, String> kvs = [:]) {
        new Properties().with {
            kvs.each {
                // To deal with GString
                put(it.key.toString(), it.value.toString())
            }
            it
        }
    }

}
