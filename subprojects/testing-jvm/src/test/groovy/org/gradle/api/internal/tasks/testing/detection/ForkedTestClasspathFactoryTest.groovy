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
    private static final int NUM_INTERNAL_JARS = 19
    private static final int NUM_EXTERNAL_JARS = 6

    ModuleRegistry moduleRegistry = Mock(ModuleRegistry) {
        getModule(_) >> { module(it[0], false) }
        getExternalModule(_) >> { module(it[0], true) }
    }

    def runtimeClasses = new TestClassDetector()
    ForkedTestClasspathFactory underTest = new ForkedTestClasspathFactory(moduleRegistry, (cls, mod) -> this.runtimeClasses)

    def "creates a limited implementation classpath"() {
        when:
        def framework = newFramework(false, [], [], [], [])
        def classpath = underTest.create([new File("cls.jar")], [new File("mod.jar")], framework, false)

        then:
        classpath.applicationClasspath == [new File("cls.jar")]
        classpath.applicationModulepath == [new File("mod.jar")]
        classpath.implementationClasspath.size() == 25
        classpath.implementationClasspath.findAll { it.toString().endsWith("-internal.jar") }.size() == NUM_INTERNAL_JARS
        classpath.implementationClasspath.findAll { it.toString().endsWith("-external.jar") }.size() == NUM_EXTERNAL_JARS
        classpath.implementationModulepath.isEmpty()
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
            new TestFrameworkDistributionModule(name, Pattern.compile("$name-1.*\\.jar"), "org.example.$name")
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
