/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection

import org.gradle.api.internal.classpath.Module
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.TestFrameworkDistributionModule
import org.gradle.internal.classpath.ClassPath
import spock.lang.Specification

import java.util.regex.Pattern

/**
 * Tests {@link ForkedTestClasspathFactory}.
 */
class ForkedTestClasspathFactoryTest extends Specification {

    // The number of internal and external implementation jars loaded from the distribution regardless of framework.
    private static final int NUM_INTERNAL_JARS = 20
    private static final int NUM_EXTERNAL_JARS = 6

    ModuleRegistry moduleRegistry = Mock(ModuleRegistry) {
        getModule(_) >> { module(it[0], false) }
        getExternalModule(_) >> { module(it[0], true) }
    }

    def runtimeClasses = Spy(TestClassDetector)
    def classDetectorFactory = Mock(ForkedTestClasspathFactory.ClassDetectorFactory) {
        create(_, _) >> { runtimeClasses }
    }
    ForkedTestClasspathFactory underTest = new ForkedTestClasspathFactory(moduleRegistry, classDetectorFactory)

    def "creates a limited implementation classpath"() {
        when:
        def framework = newFramework(false, [], [], [], [])
        def classpath = underTest.create([new File("cls.jar")], [new File("mod.jar")], framework, false)

        then:
        classpath.applicationClasspath == [new File("cls.jar")]
        classpath.applicationModulepath == [new File("mod.jar")]
        classpath.implementationClasspath.size() == 26
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS
        classpath.implementationModulepath.isEmpty()

        0 * classDetectorFactory._
    }

    def "adds framework dependencies to classpath when test is not module"() {
        when:
        def framework = newFramework(true, ["app-cls"], ["app-mod"], ["impl-cls"], ["impl-mod"])
        def classpath = underTest.create([new File("cls.jar")], [new File("mod.jar")], framework, false)

        then:
        classpath.applicationClasspath == [new File("cls.jar"), new File("app-cls-external.jar"), new File("app-mod-external.jar")]
        classpath.applicationModulepath == [new File("mod.jar")]
        classpath.implementationClasspath.size() == NUM_INTERNAL_JARS + NUM_EXTERNAL_JARS + 2
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS + 2
        classpath.implementationClasspath.takeRight(2) == [new URL("file://impl-cls-external.jar"), new URL("file://impl-mod-external.jar")]
        classpath.implementationModulepath.isEmpty()
    }

    def "adds framework dependencies to classpath and modulepath when test is module"() {
        when:
        def framework = newFramework(true, ["app-cls"], ["app-mod"], ["impl-cls"], ["impl-mod"])
        def classpath = underTest.create([new File("cls.jar")], [new File("mod.jar")], framework, true)

        then:
        classpath.applicationClasspath == [new File("cls.jar"), new File("app-cls-external.jar")]
        classpath.applicationModulepath == [new File("mod.jar"), new File("app-mod-external.jar")]
        classpath.implementationClasspath.size() == NUM_INTERNAL_JARS + NUM_EXTERNAL_JARS + 1
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS + 1
        classpath.implementationClasspath.last() == new URL("file://impl-cls-external.jar")
        classpath.implementationModulepath == [new URL("file://impl-mod-external.jar")]
    }

    def "does not load framework dependencies from distribution if they are on the test runtime classpath already with matching jar names"() {
        when:
        def framework = newFramework(true, ["app-cls"], ["app-mod"], ["impl-cls"], ["impl-mod"])
        def classpath = underTest.create([
            new File("app-cls-1.0.jar"), new File("app-mod-1.0.jar"), new File("impl-cls-1.0.jar"), new File("impl-mod-1.0.jar")
        ], [], framework, true)

        then:
        classpath.applicationClasspath == [new File("app-cls-1.0.jar"), new File("app-mod-1.0.jar"), new File("impl-cls-1.0.jar"), new File("impl-mod-1.0.jar")]
        classpath.applicationModulepath.isEmpty()
        classpath.implementationClasspath.size() == NUM_INTERNAL_JARS + NUM_EXTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS
        classpath.implementationModulepath.isEmpty()

        0 * classDetectorFactory._
    }

    def "does not load framework dependencies from distribution if they are on the test runtime modulepath already with matching jar names"() {
        when:
        def framework = newFramework(true, ["app-cls"], ["app-mod"], ["impl-cls"], ["impl-mod"])
        def classpath = underTest.create([], [
            new File("app-cls-1.0.jar"), new File("app-mod-1.0.jar"), new File("impl-cls-1.0.jar"), new File("impl-mod-1.0.jar")
        ], framework, true)

        then:
        classpath.applicationClasspath.isEmpty()
        classpath.applicationModulepath == [new File("app-cls-1.0.jar"), new File("app-mod-1.0.jar"), new File("impl-cls-1.0.jar"), new File("impl-mod-1.0.jar")]
        classpath.implementationClasspath.size() == NUM_INTERNAL_JARS + NUM_EXTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS
        classpath.implementationModulepath.isEmpty()

        0 * classDetectorFactory._
    }

    def "loads all framework dependencies from distribution even if some are already available with matching jar names"() {
        when:
        def cpFiles = cp.collect { new File("$it-1.0.jar") }
        def mpFiles = mp.collect { new File("$it-1.0.jar") }

        def framework = newFramework(true, ["a", "b"], ["c", "d"], ["e", "f"], ["g", "h"])
        def classpath = underTest.create(cpFiles, mpFiles, framework, true)

        then:
        if (loadsAll) {
            assert classpath.applicationClasspath.takeRight(2) == ["a", "b"].collect { new File("$it-external.jar") }
            assert classpath.applicationModulepath.takeRight(2) == ["c", "d"].collect { new File("$it-external.jar") }
            assert classpath.implementationClasspath.takeRight(2) == ["e", "f"].collect { new URL("file://$it-external.jar") }
            assert classpath.implementationModulepath == ["g", "h"].collect { new URL("file://$it-external.jar") }
        } else {
            assert classpath.applicationClasspath == cpFiles
            assert classpath.applicationModulepath == mpFiles
            assert classpath.implementationClasspath.size() == 26
            assert classpath.implementationModulepath.isEmpty()
        }

        where:
        cp                                       | mp                                       | loadsAll
        []                                       | []                                       | true
        ["test"]                                 | []                                       | true
        ["m", "q"]                               | ["l", "t"]                               | true
        ["a"]                                    | []                                       | true
        []                                       | ["a"]                                    | true
        ["c"]                                    | []                                       | true
        []                                       | ["c"]                                    | true
        ["e"]                                    | []                                       | true
        []                                       | ["e"]                                    | true
        ["g"]                                    | []                                       | true
        []                                       | ["g"]                                    | true
        ["test", "a"]                            | []                                       | true
        []                                       | ["test", "a"]                            | true
        ["a", "b", "c", "d"]                     | ["e", "f", "g", "h"]                     | false
        ["a", "b", "c", "d", "e", "f", "g", "h"] | []                                       | false
        []                                       | ["a", "b", "c", "d", "e", "f", "g", "h"] | false
        ["a", "c", "e", "g"]                     | ["b", "d", "f", "h"]                     | false
        ["b", "d", "f", "h"]                     | ["a", "c", "e", "g"]                     | false
    }

    def "can detect class names from classloader if jar names not found"() {
        given:
        classes.forEach(it -> runtimeClasses.add("org.$it"))

        when:
        def cpFiles = cp.collect { new File("$it-1.0.jar") }
        def mpFiles = mp.collect { new File("$it-1.0.jar") }

        def framework = newFramework(true, ["a", "b"], ["c", "d"], ["e", "f"], ["g", "h"])
        def classpath = underTest.create(cpFiles, mpFiles, framework, true)

        then:
        (["a", "b", "c", "d", "e", "f", "g", "h"] - (cp + mp)).forEach {
            1 * runtimeClasses.hasClass("org.$it")
        }
        0 * runtimeClasses.hasClass(_)

        then:
        if (loadsAll) {
            assert classpath.applicationClasspath.takeRight(2) == ["a", "b"].collect { new File("$it-external.jar") }
            assert classpath.applicationModulepath.takeRight(2) == ["c", "d"].collect { new File("$it-external.jar") }
            assert classpath.implementationClasspath.takeRight(2) == ["e", "f"].collect { new URL("file://$it-external.jar") }
            assert classpath.implementationModulepath == ["g", "h"].collect { new URL("file://$it-external.jar") }
        } else {
            assert classpath.applicationClasspath == cpFiles
            assert classpath.applicationModulepath == mpFiles
            assert classpath.implementationClasspath.size() == 26
            assert classpath.implementationModulepath.isEmpty()
        }

        where:
        cp                   | mp                   | classes                                   | loadsAll
        []                   | []                   | []                                        | true
        []                   | []                   | ["a"]                                     | true
        []                   | []                   | ["b"]                                     | true
        []                   | []                   | ["c"]                                     | true
        []                   | []                   | ["d"]                                     | true
        []                   | []                   | ["e"]                                     | true
        []                   | []                   | ["f"]                                     | true
        []                   | []                   | ["g"]                                     | true
        []                   | []                   | ["h"]                                     | true
        ["b", "c", "f", "g"] | []                   | ["b", "c", "f", "g"]                      | true
        []                   | ["b", "c", "f", "g"] | ["b", "c", "f", "g"]                      | true
        ["a", "d", "e", "h"] | []                   | ["b", "c", "f", "g"]                      | false
        []                   | ["a", "d", "e", "h"] | ["b", "c", "f", "g"]                      | false
        []                   | []                   | ["a", "b", "c", "d", "e", "f", "g", "h"]  | false
    }

    def module(String module, boolean external) {
        String extra = external ? "external" : "internal"
        return Mock(Module) {
            getImplementationClasspath() >> {
                Mock(ClassPath) {
                    getAsURLs() >> { [new URL("file://${module}-${extra}.jar")] }
                    getAsFiles() >> { [new File("${module}-${extra}.jar")] }
                }
            }
        }
    }

    TestFramework newFramework(
        boolean useDependencies,
        List<String> appClasses,
        List<String> appModules,
        List<String> implClasses,
        List<String> implModules
    ) {
        def asDistModule = { String name ->
            new TestFrameworkDistributionModule(name, Pattern.compile("$name-1.*\\.jar"), "org.$name")
        }
        return Mock(TestFramework) {
            getUseDistributionDependencies() >> useDependencies
            getWorkerApplicationClasspathModules() >> appClasses.collect { asDistModule(it) }
            getWorkerApplicationModulepathModules() >> appModules.collect { asDistModule(it) }
            getWorkerImplementationClasspathModules() >> implClasses.collect { asDistModule(it) }
            getWorkerImplementationModulepathModules() >> implModules.collect { asDistModule(it) }
        }
    }

    static class TestClassDetector implements ForkedTestClasspathFactory.ClassDetector {

        Set<String> testClasses = []

        void add(String className) {
            testClasses.add(className)
        }

        @Override
        boolean hasClass(String className) {
            return testClasses.contains(className)
        }

        @Override
        void close() throws IOException {}
    }
}
