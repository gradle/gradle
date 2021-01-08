/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.inputs.undeclared

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest
import spock.lang.Issue
import spock.lang.Unroll

import java.util.function.Supplier

class UndeclaredBuildInputsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    @Unroll
    def "reports build logic reading a system property set #mechanism.description via the Java API"() {
        buildFile << """
            // not declared
            System.getProperty("CI")
        """

        when:
        mechanism.setup(this)
        configurationCacheFails(*mechanism.gradleArgs)

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': read system property 'CI'")
        }
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsNormalizedString("Read system property 'CI'"))

        where:
        mechanism << SystemPropertyInjection.all("CI", "false")
    }

    @Issue("https://github.com/gradle/gradle/issues/13569")
    @Unroll
    def "reports build logic reading system properties using GString parameters - #expression"() {
        buildFile << """
            def ci = "ci"
            def value = "value"
            println "CI1 = " + $expression
        """

        when:
        configurationCacheRun()

        then:
        outputContains("CI1 = ${notDefined}")

        when:
        configurationCacheFails("-DCI1=${value}")

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': read system property 'CI1'")
        }
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertThatCause(containsNormalizedString("Read system property 'CI1'"))

        outputContains("CI1 = ${expectedValue}")

        where:
        expression                                                             | notDefined | value     | expectedValue
        'System.getProperty("${ci.toUpperCase()}1")'                           | ""         | "defined" | "defined"
        'System.getProperty("${ci.toUpperCase()}1", "${value.toUpperCase()}")' | "VALUE"    | "defined" | "defined"
        'Boolean.getBoolean("${ci.toUpperCase()}1")'                           | "false"    | "true"    | "true"
        'Integer.getInteger("${ci.toUpperCase()}1")'                           | "null"     | "123"     | "123"
        'Long.getLong("${ci.toUpperCase()}1")'                                 | "null"     | "123"     | "123"
    }

    @Issue("https://github.com/gradle/gradle/issues/13652")
    @Unroll
    def "reports build logic reading system properties with null defaults - #expression"() {
        buildFile << """
            println "CI1 = " + $expression
        """

        when:
        configurationCacheRun()

        then:
        outputContains("CI1 = null")

        when:
        configurationCacheFails("-DCI1=${value}")

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': read system property 'CI1'")
        }
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(2)
        failure.assertThatCause(containsNormalizedString("Read system property 'CI1'"))

        outputContains("CI1 = ${expectedValue}")

        where:
        expression                        | value     | expectedValue
        'System.getProperty("CI1", null)' | "defined" | "defined"
        'Integer.getInteger("CI1", null)' | "123"     | "123"
        'Long.getLong("CI1", null)'       | "123"     | "123"
    }

    @Unroll
    def "reports buildSrc build logic and tasks reading a system property set #mechanism.description via the Java API"() {
        def buildSrcBuildFile = file("buildSrc/build.gradle")
        buildSrcBuildFile << """
            System.getProperty("CI")
            tasks.classes.doLast {
                System.getProperty("CI2")
            }
        """

        when:
        mechanism.setup(this)
        configurationCacheFails(*mechanism.gradleArgs, "-DCI2=true")

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file '${relativePath('buildSrc/build.gradle')}': read system property 'CI'")
            withProblem("Build file '${relativePath('buildSrc/build.gradle')}': read system property 'CI2'")
        }
        failure.assertHasFileName("Build file '${buildSrcBuildFile}'")
        failure.assertHasLineNumber(2)
        failure.assertThatCause(containsNormalizedString("Read system property 'CI'"))
        failure.assertThatCause(containsNormalizedString("Read system property 'CI2'"))

        where:
        mechanism << SystemPropertyInjection.all("CI", "false")
    }

    @Unroll
    def "build logic can read system property with no value without declaring access and loading fails when value set using #mechanism.description"() {
        file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    System.out.println("CI = " + System.getProperty("CI"));
                }
            }
        """
        buildFile << """
            apply plugin: SneakyPlugin
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun()

        then:
        outputContains("CI = null")

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateLoaded()
        noExceptionThrown()

        when:
        mechanism.setup(this)
        configurationCacheFails(*mechanism.gradleArgs)

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin class 'SneakyPlugin': read system property 'CI'")
        }

        where:
        mechanism << SystemPropertyInjection.all("CI", "false")
    }

    @Unroll
    def "build logic can read system property with a default using #read.javaExpression without declaring access"() {
        file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    System.out.println("CI = " + ${read.javaExpression});
                }
            }
        """
        buildFile << """
            apply plugin: SneakyPlugin
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun()

        then:
        outputContains("CI = $defaultValue")

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateLoaded()
        noExceptionThrown()

        when:
        configurationCacheRunLenient("-DCI=$defaultValue") // use the default value

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withProblem("Plugin class 'SneakyPlugin': read system property 'CI'")
        }

        when:
        configurationCacheRun("-DCI=$newValue") // undeclared inputs are not treated as inputs, but probably should be

        then:
        configurationCache.assertStateLoaded()
        noExceptionThrown()

        where:
        read                                                                        | defaultValue | newValue
        SystemPropertyRead.systemGetPropertyWithDefault("CI", "false")              | "false"      | "true"
        SystemPropertyRead.systemGetPropertiesGetPropertyWithDefault("CI", "false") | "false"      | "true"
        SystemPropertyRead.integerGetIntegerWithPrimitiveDefault("CI", 123)         | "123"        | "456"
        SystemPropertyRead.integerGetIntegerWithIntegerDefault("CI", 123)           | "123"        | "456"
        SystemPropertyRead.longGetLongWithPrimitiveDefault("CI", 123)               | "123"        | "456"
        SystemPropertyRead.longGetLongWithLongDefault("CI", 123)                    | "123"        | "456"
    }

    @Unroll
    def "build logic can read standard system property #prop without declaring access"() {
        file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    System.out.println("$prop = " + System.getProperty("$prop"));
                }
            }
        """
        buildFile << """
            apply plugin: SneakyPlugin
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun()

        then:
        outputContains("$prop = ")

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateLoaded()
        noExceptionThrown()

        where:
        prop << [
            "os.name",
            "os.version",
            "os.arch",
            "java.version",
            "java.version.date",
            "java.vendor",
            "java.vendor.url",
            "java.vendor.version",
            "java.specification.version",
            "java.specification.vendor",
            "java.specification.name",
            "java.vm.version",
            "java.vm.specification.version",
            "java.vm.specification.vendor",
            "java.vm.specification.name",
            "java.vm.version",
            "java.vm.vendor",
            "java.vm.name",
            "java.class.version",
            "java.home",
            "java.class.path",
            "java.library.path",
            "java.compiler",
            "file.separator",
            "path.separator",
            "line.separator",
            "user.name",
            "user.home"
            // Not java.io.tmpdir and user.dir at this stage
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/13155")
    def "plugin can bundle multiple resources with the same name"() {
        file("buildSrc/build.gradle") << """
            jar.from('resources1')
            jar.from('resources2')
            jar.duplicatesStrategy = DuplicatesStrategy.INCLUDE
        """
        file("buildSrc/src/main/groovy/SomePlugin.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    getClass().classLoader.getResources("file.txt").each { url ->
                        println("resource = " + url.text)
                    }
                }
            }
        """
        file("buildSrc/resources1/file.txt") << "one"
        file("buildSrc/resources2/file.txt") << "two"
        buildFile << """
            apply plugin: SomePlugin
        """

        when:
        configurationCacheRun()

        then:
        // The JVM only exposes one of the resources
        output.count("resource = ") == 1
        outputContains("resource = two")
    }

    @Issue("https://github.com/gradle/gradle/issues/13325")
    def "Java plugin can use serializable lambda and action lambda"() {
        file("buildSrc/src/main/java/SerializableSupplier.java") << """
            import ${Serializable.name};
            import ${Supplier.name};

            // An interface with method that creates a serializable lambda instance
            public interface SerializableSupplier extends Supplier<String>, Serializable {
                static SerializableSupplier of(String value) {
                    return () -> value;
                }
            }
        """
        file("buildSrc/src/main/java/SomePlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};
            import ${Serializable.name};

            // A class with a method that creates a serializable lambda instance and an Action lambda
            public class SomePlugin implements Plugin<Project> {
                interface SerializableAction<T> extends Serializable {
                    void run(T value);
                }

                public void apply(Project project) {
                    SerializableSupplier supplier = SerializableSupplier.of("value");
                    SerializableAction<String> action = v -> { System.out.println("value = " + v); };
                    project.getTasks().register("task", t1 -> {
                        t1.doLast(t2 -> {
                            action.run(supplier.get());
                        });
                    });
                }
            }
        """
        buildFile << """
            apply plugin: SomePlugin
        """

        when:
        configurationCacheRun("task")
        configurationCacheRun("task")

        then:
        outputContains("value = value")
    }
}
