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

import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultModuleRegistryTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    TestFile runtimeDep
    TestFile resourcesDir
    TestFile jarFile
    TestFile distDir

    def setup() {
        distDir = tmpDir.createDir("dist")

        distDir.createDir("lib")
        distDir.createDir("lib/plugins")
        distDir.createDir("lib/plugins/sonar")
        runtimeDep = distDir.createZip("lib/dep-1.2.jar")

        resourcesDir = tmpDir.createDir("some-module/build/resources/main")
        def properties = new Properties()
        properties.runtime = 'dep-1.2.jar'
        properties.projects = ''
        resourcesDir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        jarFile = distDir.file("lib/gradle-some-module-5.1.jar")
        resourcesDir.zipTo(jarFile)
    }

    def "locates module using jar in distribution image"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [jarFile]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "locates module using jar from runtime ClassLoader"() {
        given:
        def cl = new URLClassLoader([jarFile, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [jarFile]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "locates module using manifest from runtime ClassLoader when run from IDEA"() {
        given:
        def classesDir = tmpDir.createDir("out/production/someModule")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def ignoredDir = tmpDir.createDir("ignore-me-out/production/someModule")
        def cl = new URLClassLoader([ignoredDir, classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [classesDir, resourcesDir, staticResourcesDir]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "locates module using manifest from runtime ClassLoader when run from Eclipse"() {
        given:
        def classesDir = tmpDir.createDir("some-module/bin")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def cl = new URLClassLoader([classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [classesDir, resourcesDir, staticResourcesDir]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "locates module using manifest from runtime ClassLoader when run from build"() {
        given:
        def classesDir = tmpDir.createDir("some-module/build/classes/main")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def cl = new URLClassLoader([classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [classesDir, resourcesDir, staticResourcesDir]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "locates module using jar from additional classpath"() {
        given:
        def registry = new DefaultModuleRegistry(new DefaultClassPath([jarFile, runtimeDep]))

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [jarFile]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "locates module using manifest from additional classpath when run from IDEA"() {
        given:
        def classesDir = tmpDir.createDir("out/production/someModule")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def ignoredDir = tmpDir.createDir("ignore-me-out/production/someModule")
        def registry = new DefaultModuleRegistry(new DefaultClassPath([ignoredDir, classesDir, resourcesDir, staticResourcesDir, runtimeDep]))

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [classesDir, resourcesDir, staticResourcesDir]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "requires no additional module classpath when run from distribution"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        registry.additionalClassPath.empty
    }

    def "requires additional module classpath when no distribution available"() {
        given:
        def classesDir = tmpDir.createDir("out/production/someModule")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def ignoredDir = tmpDir.createDir("ignore-me-out/production/someModule")
        def cl = new URLClassLoader([ignoredDir, classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        expect:
        registry.additionalClassPath.asFiles.containsAll([classesDir, staticResourcesDir, resourcesDir])
    }

    def "handles empty classpaths in manifest"() {
        given:
        def properties = new Properties()
        properties.runtime = ''
        properties.projects = ''
        resourcesDir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        def cl = new URLClassLoader([resourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.runtimeClasspath.empty
        module.requiredModules.empty
    }

    def "extracts required modules from manifest"() {
        given:
        def properties = new Properties()
        properties.projects = 'gradle-module-2'
        def module1Dir = tmpDir.createDir("some-module/build/resources/main")
        module1Dir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        properties = new Properties()
        def module2Dir = tmpDir.createDir("module-2/build/resources/main")
        module2Dir.file("gradle-module-2-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        def cl = new URLClassLoader([module1Dir, module2Dir].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.requiredModules as List == [registry.getModule("gradle-module-2")]
    }

    def "builds transitive closure of required modules"() {
        given:
        def properties = new Properties()
        properties.projects = 'gradle-module-2'
        def module1Dir = tmpDir.createDir("some-module/build/resources/main")
        module1Dir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        properties = new Properties()
        properties.projects = 'gradle-module-3'
        def module2Dir = tmpDir.createDir("module-2/build/resources/main")
        module2Dir.file("gradle-module-2-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        properties = new Properties()
        properties.projects = ''
        def module3Dir = tmpDir.createDir("module-3/build/resources/main")
        module3Dir.file("gradle-module-3-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        def cl = new URLClassLoader([module1Dir, module2Dir, module3Dir].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.allRequiredModules as List == [module, registry.getModule("gradle-module-2"), registry.getModule("gradle-module-3")]
    }

    def "fails when classpath does not contain manifest resource"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, null as File)

        when:
        registry.getModule("gradle-some-module")

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot locate manifest for module 'gradle-some-module' in classpath."
    }

    def "fails when classpath and distribution image do not contain manifest"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        when:
        registry.getModule("gradle-other-module")

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot locate JAR for module 'gradle-other-module' in distribution directory '$distDir'."
    }

    def "locates an external module as a JAR on the classpath"() {
        given:
        def cl = new URLClassLoader([runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getExternalModule("dep")
        module.implementationClasspath.asFiles == [runtimeDep]
        module.runtimeClasspath.empty
    }

    def "locates an external module as a JAR in the distribution image when not available on the classpath"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getExternalModule("dep")
        module.implementationClasspath.asFiles == [runtimeDep]
        module.runtimeClasspath.empty
    }

    def "fails when external module cannot be found"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        when:
        registry.getExternalModule("unknown")

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot locate JAR for module 'unknown' in distribution directory '$distDir'."
    }

    def "ignores jars which have the same prefix as an external module"() {
        given:
        distDir.createFile("dep-launcher-1.2.jar")
        distDir.createFile("dep-launcher-1.2-beta-3.jar")
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getExternalModule("dep")
        module.implementationClasspath.asFiles == [runtimeDep]
        module.runtimeClasspath.empty
    }

    def "also looks in subdirectories of plugins directory when searching for external modules"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        when:
        def sonarDependency = distDir.createZip("lib/plugins/sonar/sonar-dependency-1.2.jar")

        then:
        def module = registry.getExternalModule("sonar-dependency")
        module.implementationClasspath.asFiles == [sonarDependency]
        module.runtimeClasspath.empty
    }
}
