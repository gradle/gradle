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

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
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

    def "can try to find non-existent module"() {
        expect:
        registry.findModule("foo") == null
    }

    def "fails when distribution does not contain module"() {
        when:
        registry.getModule("other-module")

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot find module 'other-module' in distribution directory '$distDir'."
    }

    def "fails when properties does not contain dependencies"() {
        given:
        def propertiesFile = storeProperties(properties(), "foo", libDir)

        when:
        registry.getModule("foo")

        then:
        IllegalArgumentException e = thrown()
        e.message == "Missing required property 'dependencies' in module properties file '$propertiesFile'."
    }

    def "permits modules without a jar file"() {
        given:
        storeProperties(properties(dependencies: ""), "foo", libDir)

        when:
        def module = registry.getModule("foo")

        then:
        module.implementationClasspath.empty
    }

    def "fails when module does not have declared jar file"() {
        given:
        def module = createModule("foo", [])
        module.jar.delete()

        when:
        registry.getModule("foo")

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot find JAR 'foo-1.jar' required by module 'foo' distribution directory."
    }

    def "fails when a transitive dependency is missing"() {
        given:
        createModule("root", ["child"])
        createModule("child", ["missing"])

        when:
        registry.getRuntimeModules([registry.getModule("root")])

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot find module 'missing' in distribution directory '$distDir'."
    }

    def "module may have no dependencies"() {
        when:
        TestModule lib = createModule("lib", [], libDir)

        then:
        validateModule(lib, [])
    }

    def "modules may have no alias"() {
        given:
        TestModule lib = createModule("lib", [], libDir)

        when:
        def module = registry.getModule(lib.name)

        then:
        module.alias == null
    }

    def "modules may not define partial alias"() {
        given:
        storeProperties(properties(
            dependencies: "",
            "alias.group": "group",
            "alias.name": "name"
        ), "foo", libDir)

        when:
        registry.getModule("foo")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot create module 'foo' with partial module alias."
    }

    def "modules may may define alias"() {
        given:
        storeProperties(properties(
            dependencies: "",
            "alias.group": "group",
            "alias.name": "name",
            "alias.version": "version"
        ), "foo", libDir)

        when:
        def module = registry.getModule("foo")

        then:
        module.alias.group == "group"
        module.alias.name == "name"
        module.alias.version == "version"
    }

    def "modules in lib can depend on modules from lib and plugins"() {
        given:
        TestModule libDep = createModule("libDep", [], libDir)
        TestModule pluginsDep = createModule("pluginsDep", [], pluginsDir)

        when:
        TestModule someModule = createModule("some-module", ["${libDep.name}", "${pluginsDep.name}"], libDir)

        then:
        validateModule(someModule, ["libDep", "pluginsDep"])
    }

    def "modules in plugins can depend on modules from lib and plugins"() {
        given:
        TestModule libDep = createModule("libDep", [], libDir)
        TestModule pluginsDep = createModule("pluginsDep", [], pluginsDir)

        when:
        TestModule someModule = createModule("some-module", ["${libDep.name}", "${pluginsDep.name}"], pluginsDir)

        then:
        validateModule(someModule, ["libDep", "pluginsDep"])
    }

    def "order of dependencies is preserved"() {
        given:
        TestModule dep1 = createModule("dep1", [], libDir)
        TestModule dep2 = createModule("dep2", [], pluginsDir)

        when:
        TestModule module1 = createModule("module1", ["${dep1.name}", "${dep2.name}"], pluginsDir)
        TestModule module2 = createModule("module2", ["${dep2.name}", "${dep1.name}"], pluginsDir)

        then:
        validateModule(module1, ["dep1", "dep2"])
        validateModule(module2, ["dep2", "dep1"])
    }

    def "modules can depend on other modules"() {
        given:
        createModule("lib-module", [], libDir)
        createModule("plugin-module", [], pluginsDir)
        createModule("root-lib-module", ["lib-module", "plugin-module"], libDir)
        createModule("root-plugin-module", ["lib-module", "plugin-module"], pluginsDir)

        expect:
        validateClasspath("root-lib-module", ["root-lib-module", "lib-module", "plugin-module"])
        validateClasspath("root-plugin-module", ["root-plugin-module", "lib-module", "plugin-module"])
    }

    def "builds transitive closure of required modules"() {
        given:
        createModule("module-1", ["module-2"])
        createModule("module-2", ["module-3", "module-4"])
        createModule("module-3", [])
        createModule("module-4", ["module-5"])
        createModule("module-5", [])

        expect:
        validateClasspath("module-1", ["module-1", "module-2", "module-3", "module-4", "module-5"])
    }

    def "supports cycles between modules"() {
        given:
        createModule("module-1", ["module-2"])
        createModule("module-2", ["module-1"])

        expect:
        validateClasspath("module-1", ["module-1", "module-2"])
        validateClasspath("module-2", ["module-2", "module-1"])
    }

    def "can get runtime classpath of multiple modules"() {
        createModule("module-1", ["module-2"])
        createModule("module-2", [])
        createModule("module-3", ["module-4"])
        createModule("module-4", [])

        expect:
        registry.getRuntimeModules([
            registry.getModule("module-1"),
            registry.getModule("module-3")
        ]) as List == [
            registry.getModule("module-1"),
            registry.getModule("module-3"),
            registry.getModule("module-2"),
            registry.getModule("module-4")
        ]
    }

    def "can get runtime classpath of multiple modules that share common subgraph"() {
        createModule("module-1", ["module-2"])
        createModule("module-2", ["module-5"])
        createModule("module-3", ["module-4"])
        createModule("module-4", ["module-5"])
        createModule("module-5", ["module-6"])
        createModule("module-6", [])

        expect:
        registry.getRuntimeModules([
            registry.getModule("module-1"),
            registry.getModule("module-3")
        ]) as List == [
            registry.getModule("module-1"),
            registry.getModule("module-3"),
            registry.getModule("module-2"),
            registry.getModule("module-4"),
            registry.getModule("module-5"),
            registry.getModule("module-6")
        ]
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

        def properties = properties(jarFile: jarFile.name, dependencies: dependencies.join(","))
        TestFile propertiesFile = storeProperties(properties, moduleName, destinationDir)

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

    private static TestFile storeProperties(Properties properties, String moduleName, TestFile destinationDir) {
        def propertiesFile = destinationDir.file("${moduleName}.properties")
        GUtil.saveProperties(properties, propertiesFile)
        propertiesFile
    }

    void validateModule(TestModule testModule, List<String> dependencies) {
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

    void validateClasspath(String moduleName, List<String> runtimeClasspath) {
        def expectedClasspathModules = runtimeClasspath.collect { registry.getModule(it) }
        def expectedClasspath = toClasspath(expectedClasspathModules)

        assert registry.getRuntimeClasspath(moduleName) == expectedClasspath
        assert registry.getRuntimeClasspath([registry.getModule(moduleName)]) == expectedClasspath
        assert registry.getRuntimeModules([registry.getModule(moduleName)]) as List == expectedClasspathModules
    }

    ClassPath toClasspath(Iterable<Module> modules) {
        return DefaultClassPath.of(modules.collect { it.implementationClasspath.asFiles }.flatten())
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
