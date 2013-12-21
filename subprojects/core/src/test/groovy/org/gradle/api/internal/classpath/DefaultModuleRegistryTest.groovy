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
        runtimeDep = distDir.createZip("lib/dep-1.2.jar")

        resourcesDir = tmpDir.createDir("classes")
        def properties = new Properties()
        properties.runtime = 'dep-1.2.jar'
        properties.projects = ''
        resourcesDir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        jarFile = distDir.file("lib/gradle-some-module-5.1.jar")
        resourcesDir.zipTo(jarFile)
    }

    def "uses manifest from jar in distribution image"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [jarFile]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "uses manifest from classpath when run from IDEA"() {
        given:
        def classesDir = tmpDir.createDir("out/production/someModule")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def ignoredDir = tmpDir.createDir("ignore-me-out/production/someModule")
        def cl = new URLClassLoader([ignoredDir, classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [classesDir, staticResourcesDir, resourcesDir]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "uses manifest from classpath when run from Eclipse"() {
        given:
        def classesDir = tmpDir.createDir("some-module/bin")
        def staticResourcesDir = tmpDir.createDir("some-module/src/main/resources")
        def cl = new URLClassLoader([classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [classesDir, staticResourcesDir, resourcesDir]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "uses manifest from classpath when run from build"() {
        given:
        def classesDir = tmpDir.createDir("some-module/build/classes/main")
        def staticResourcesDir = tmpDir.createDir("some-module/build/resources/main")
        def cl = new URLClassLoader([classesDir, resourcesDir, staticResourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [classesDir, staticResourcesDir, resourcesDir]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "uses manifest from a jar on the classpath"() {
        given:
        def cl = new URLClassLoader([jarFile, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, distDir)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.implementationClasspath.asFiles == [jarFile]
        module.runtimeClasspath.asFiles == [runtimeDep]
    }

    def "handles empty classpaths in manifest"() {
        given:
        def properties = new Properties()
        properties.runtime = ''
        properties.projects = ''
        resourcesDir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        def cl = new URLClassLoader([resourcesDir, runtimeDep].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.runtimeClasspath.empty
        module.requiredModules.empty
    }

    def "extracts required modules from manifest"() {
        given:
        def properties = new Properties()
        properties.projects = 'gradle-module-2'
        resourcesDir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        properties = new Properties()
        resourcesDir.file("gradle-module-2-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        def cl = new URLClassLoader([resourcesDir].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.requiredModules as List == [registry.getModule("gradle-module-2")]
    }

    def "builds transitive closure of required modules"() {
        given:
        def properties = new Properties()
        properties.projects = 'gradle-module-2'
        resourcesDir.file("gradle-some-module-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        properties = new Properties()
        properties.projects = 'gradle-module-3'
        resourcesDir.file("gradle-module-2-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        properties = new Properties()
        properties.projects = ''
        resourcesDir.file("gradle-module-3-classpath.properties").withOutputStream { outstr -> properties.save(outstr, "header") }

        def cl = new URLClassLoader([resourcesDir].collect { it.toURI().toURL() } as URL[])
        def registry = new DefaultModuleRegistry(cl, null)

        expect:
        def module = registry.getModule("gradle-some-module")
        module.allRequiredModules as List == [module, registry.getModule("gradle-module-2"), registry.getModule("gradle-module-3")]
    }

    def "fails when classpath does not contain manifest resource"() {
        given:
        def cl = new URLClassLoader([] as URL[])
        def registry = new DefaultModuleRegistry(cl, null)

        when:
        registry.getModule("gradle-some-module")

        then:
        UnknownModuleException e = thrown()
        e.message == "Cannot locate classpath manifest for module 'gradle-some-module' in classpath."
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
}
