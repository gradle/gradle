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
    TestFile libDir = distDir.createDir("lib")
    TestFile pluginsDir = libDir.createDir("plugins")

    void validate(TestModule testModule, List<String> dependencies) {
        def moduleName = testModule.name

        def found = registry.findModule(moduleName)
        assert found != null

        def module = registry.getModule(moduleName)
        assert module.is(found)

        assert module.name == moduleName
        assert module.dependencyNames == dependencies
        assert module.implementationClasspath.asFiles as List == [testModule.jar]

        def classpath = registry.getRuntimeClasspath(moduleName)
        assert classpath == registry.getRuntimeClasspath([module])

        def moduleDependencies = registry.getRuntimeModules([module])
        assert moduleDependencies as List == [module] + dependencies.collect { registry.getModule(it) }
        assert classpath.asFiles == moduleDependencies.collect { it.implementationClasspath.asFiles }.flatten()
    }

    def "modules in lib can depend on modules from lib and plugins"() {
        given:
        TestModule libDep = createModule("libDep", [], libDir)
        TestModule pluginsDep = createModule("pluginsDep", [], pluginsDir)

        when:
        TestModule someModule = createModule("some-module", ["${libDep.name}", "${pluginsDep.name}"], libDir)

        then:
        validate(someModule, ["libDep", "pluginsDep"])
    }

    def "modules in plugins can depend on modules from lib and plugins"() {
        given:
        TestModule libDep = createModule("libDep", [], libDir)
        TestModule pluginsDep = createModule("pluginsDep", [], pluginsDir)

        when:
        TestModule someModule = createModule("some-module", ["${libDep.name}", "${pluginsDep.name}"], pluginsDir)

        then:
        validate(someModule, ["libDep", "pluginsDep"])
    }

    def "order of dependencies is preserved"() {
        given:
        TestModule dep1 = createModule("dep1", [], libDir)
        TestModule dep2 = createModule("dep2", [], pluginsDir)

        when:
        TestModule module1 = createModule("module1", ["${dep1.name}", "${dep2.name}"], pluginsDir)
        TestModule module2 = createModule("module2", ["${dep2.name}", "${dep1.name}"], pluginsDir)

        then:
        validate(module1, ["dep1", "dep2"])
        validate(module2, ["dep2", "dep1"])
    }

    def "handles empty classpaths in manifest"() {
        given:
        createModule("foo", [])

        when:
        def module = registry.getModule("foo")

        then:
        module.dependencyNames.empty
    }

    def "modules can depend on other modules"() {
        given:
        createModule("lib-module", [], libDir)
        createModule("plugin-module", [], pluginsDir)
        createModule("root-lib-module", ["lib-module", "plugin-module"], libDir)
        createModule("root-plugins-module", ["lib-module", "plugin-module"], pluginsDir)

        when:
        def libModule = registry.getModule("root-lib-module")
        def pluginsModule = registry.getModule("root-plugins-module")

        then:
        registry.getRuntimeModules([libModule]) as List == [libModule, registry.getModule("lib-module"), registry.getModule("plugin-module")]
        registry.getRuntimeModules([pluginsModule]) as List == [pluginsModule, registry.getModule("lib-module"), registry.getModule("plugin-module")]
    }

    def "builds transitive closure of required modules"() {
        given:
        createModule("module-1", ["module-2"])
        createModule("module-2", ["module-3"])
        createModule("module-3", [])

        when:
        def module = registry.getModule("module-1")

        then:
        registry.getRuntimeModules([module]) as List == [module, registry.getModule("module-2"), registry.getModule("module-3")]
    }

    def "supports cycles between modules"() {
        given:
        createModule("module-1", ["module-2"])
        createModule("module-2", ["module-1"])

        when:
        def module1 = registry.getModule("module-1")
        def module2 = registry.getModule("module-2")

        then:
        registry.getRuntimeModules([module1]) as List == [module1, module2]
        registry.getRuntimeModules([module2]) as List == [module2, module1]
    }

    def "fails when distribution does not contain module"() {
        when:
        registry.getModule("other-module")

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot find module 'other-module' in distribution directory '$distDir'."
    }

    def "ignores jars which have the same prefix as a module"() {
        given:
        TestModule libDep = createModule("dep", [], libDir)
        createModule("dep-extra", [], libDir)

        when:
        def module = registry.getModule("dep")

        then:
        module.implementationClasspath.asFiles == [libDep.jar]
    }

    def "also looks in subdirectories of plugins directory when searching for modules"() {
        when:
        TestModule sonarDependency = createModule("sonar-dependency", [], pluginsDir.createDir("sonar"))

        then:
        def module = registry.getModule("sonar-dependency")
        module.implementationClasspath.asFiles == [sonarDependency.jar]
    }

    ModuleRegistry cachedRegistry
    ModuleRegistry getRegistry() {
        if (cachedRegistry == null) {
            cachedRegistry = new DefaultModuleRegistry(new GradleInstallation(distDir))
        }
        cachedRegistry
    }

    TestModule createModule(String moduleName, List<String> dependencies, TestFile destinationDir = libDir) {
        TestFile jarFile = destinationDir.file("$moduleName-1.jar")
        jarFile.createNewFile()

        def propertiesFile = destinationDir.file("${moduleName}.properties")
        def properties = new Properties()
        properties["jarFile"] = jarFile.name
        properties["dependencies"] = dependencies.join(",")
        GUtil.saveProperties(properties, propertiesFile)

        new TestModule(moduleName, propertiesFile, jarFile)
    }

    TestModule createModule(String moduleName, Properties properties, TestFile destinationDir = libDir) {
        TestFile jarFile = destinationDir.file("$moduleName-1.jar")
        jarFile.createNewFile()

        def propertiesFile = destinationDir.file("${moduleName}.properties")
        GUtil.saveProperties(properties, propertiesFile)

        new TestModule(moduleName, propertiesFile, jarFile)
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

    private static class TestModule {
        String name
        TestFile properties
        TestFile jar

        TestModule(
            String name,
            TestFile properties,
            TestFile jar
        ) {
            this.name = name
            this.properties = properties
            this.jar = jar
        }
    }

}
