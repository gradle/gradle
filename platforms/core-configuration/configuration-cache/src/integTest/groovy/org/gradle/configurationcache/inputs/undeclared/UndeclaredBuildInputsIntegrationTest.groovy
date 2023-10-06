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

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.configurationcache.AbstractConfigurationCacheIntegrationTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.JarUtils
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.function.Supplier

class UndeclaredBuildInputsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def testDir = testDirectoryProvider.testDirectory

    def "reports build logic reading a system property set #mechanism.description via the Java API"() {
        buildFile << """
            // not declared
            System.getProperty("CI")
        """

        when:
        mechanism.setup(this)
        configurationCacheRun(*mechanism.gradleArgs)

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system property 'CI'")
        }

        where:
        mechanism << SystemPropertyInjection.all("CI", "false")
    }

    @Issue("https://github.com/gradle/gradle/issues/13569")
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
        configurationCacheRun("-DCI1=${value}")

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system property 'CI1'")
        }
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
    def "reports build logic reading system properties with null defaults - #expression"() {
        buildFile << """
            println "CI1 = " + $expression
        """

        when:
        configurationCacheRun()

        then:
        outputContains("CI1 = null")

        when:
        configurationCacheRun("-DCI1=${value}")

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system property 'CI1'")
        }
        outputContains("CI1 = ${expectedValue}")

        where:
        expression                        | value     | expectedValue
        'System.getProperty("CI1", null)' | "defined" | "defined"
        'Integer.getInteger("CI1", null)' | "123"     | "123"
        'Long.getLong("CI1", null)'       | "123"     | "123"
    }

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
        configurationCacheRun(*mechanism.gradleArgs, "-DCI2=true")

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file '${relativePath('buildSrc/build.gradle')}': system property 'CI'")
            withInput("Build file '${relativePath('buildSrc/build.gradle')}': system property 'CI2'")
        }

        where:
        mechanism << SystemPropertyInjection.all("CI", "false")
    }

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
        configurationCacheRun(*mechanism.gradleArgs)

        then:
        problems.assertResultHasProblems(result) {
            withInput("Plugin class 'SneakyPlugin': system property 'CI'")
        }

        where:
        mechanism << SystemPropertyInjection.all("CI", "false")
    }

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
            withInput("Plugin class 'SneakyPlugin': system property 'CI'")
        }

        when:
        configurationCacheRun("-DCI=$newValue")

        then: 'undeclared inputs are treated as inputs'
        configurationCache.assertStateStored()
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

    def "system property set at the configuration phase is restored when running from cache"() {
        given:
        buildFile("""
            $propertySetter

            tasks.register("printProperty") {
                doLast {
                    println("some.property = \${System.properties["some.property"]}")
                }
            }
        """)

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("printProperty")

        then:
        outputContains("some.property = $propertyValue")

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("some.property = $propertyValue")

        where:
        propertyValue | propertySetter
        "some.value"  | """System.setProperty("some.property", "$propertyValue")"""
        "some.value"  | """System.properties["some.property"]="$propertyValue" """
        "1"           | """System.properties["some.property"]=$propertyValue"""
    }

    def "system property removed at the configuration phase is removed when running from cache"() {
        given:
        buildFile("""
            $propertyRemover

            tasks.register("printProperty") {
                doLast {
                    println("some.property present = \${System.properties.containsKey("some.property")}")
                }
            }
        """)

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dsome.property=some.value", "printProperty")

        then:
        outputContains("some.property present = false")

        when:
        configurationCacheRun("-Dsome.property=some.value", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("some.property present = false")

        where:
        propertyRemover                                               | _
        """System.clearProperty("some.property")"""                   | _
        """System.properties.remove("some.property")"""               | _
        """System.getProperties().keySet().remove("some.property")""" | _
    }

    def "build logic can use setProperties at configuration phase"() {
        given:
        buildFile("""
            System.setProperty("some.removed.property", "removed.value")
            def newProps = new Properties()
            System.properties.forEach { k, v -> newProps.put(k, v) }
            newProps.setProperty("some.property", "some.value")
            newProps.remove("some.removed.property")

            System.setProperties(newProps)
            tasks.register("printProperty") {
                doLast {
                    println("some.property = \${System.properties.getProperty("some.property")}")
                    println("some.removed.property = \${System.properties.getProperty("some.removed.property")}")
                }
            }
        """)

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("some.property = some.value")
        outputContains("some.removed.property = null")

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("some.property = some.value")
        outputContains("some.removed.property = null")
    }

    @Requires(value = IntegTestPreconditions.NotNoDaemonExecutor, reason = """
Running with --no-daemon causes the test to fail when changing the command line because the
internal property sun.java.command changes.
""")
    def "properties set after clearing system properties with #systemPropsCleaner do not become inputs"() {
        given:
        buildFile("""
            def copiedProps = new HashMap<>(System.properties)
            $systemPropsCleaner

            copiedProps.forEach { k, v -> System.setProperty(k, v) }
            System.setProperty("some.property", "some.value")

            tasks.register("printProperty") {
                doLast {
                    println("some.property = \${System.properties.getProperty("some.property")}")
                }
            }
        """)

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("some.property = some.value")

        when:
        configurationCacheRun("-Dsome.property=other.value", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("some.property = some.value")

        where:
        systemPropsCleaner                       | _
        'System.properties.clear()'              | _
        'System.properties.keySet().clear()'     | _
        'System.properties.entrySet().clear()'   | _
        'System.setProperties(new Properties())' | _
    }

    def "system property removed after update at the configuration phase is removed when running from cache"() {
        given:
        buildFile("""
            System.setProperty("some.property", "some.value")
            System.clearProperty("some.property")

            tasks.register("printProperty") {
                doLast {
                    println("some.property present = \${System.properties.containsKey("some.property")}")
                }
            }
        """)

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Dsome.property=some.value", "printProperty")

        then:
        outputContains("some.property present = false")

        when:
        configurationCacheRun("-Dsome.property=some.value", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("some.property present = false")
    }

    def "system property added and removed at the configuration phase is removed when running from cache even if set externally"() {
        given:
        buildFile("""
            System.properties.putAll(someProperty: "some.value")  // Use putAll to avoid recording property as an input
            System.clearProperty("someProperty")

            tasks.register("printProperty") {
                doLast {
                    println("someProperty present = \${System.properties.containsKey("someProperty")}")
                }
            }
        """)

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("printProperty")

        then:
        outputContains("someProperty present = false")

        when:
        configurationCacheRun("-DsomeProperty=some.value", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("someProperty present = false")
    }

    def "non-serializable system property is reported"() {
        given:
        buildFile("""
            System.properties["some.property"] = new Thread()
        """)

        when:
        configurationCacheFails()

        then:
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': cannot serialize object of type 'java.lang.Thread', a subtype of 'java.lang.Thread', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
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

    @Issue("https://github.com/gradle/gradle/issues/25044")
    @Requires(UnitTestPreconditions.Jdk11OrLater)
    def "plugin can read file within jar"() {
        def testFile = JarUtils.jar(testDir.file("thing.jar")) {
            manifest {}

            entry("foo.txt", "bar")
        }

        buildKotlinFile << """
            import ${DefaultTask.name}
            import ${Project.name}
            import ${Plugin.name}
            import ${TaskAction.name}
            import ${File.name}
            import ${URI.name}
            import ${FileSystems.name}
            import ${Files.name}

            class SomePlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("readFileWithinJarTask", ReadFileWithinJarTask::class.java)
                }
            }

            abstract class ReadFileWithinJarTask : DefaultTask() {
                @TaskAction
                fun run() {
                    val path = File("${TextUtil.escapeString(testFile.path)}").toPath()
                    val uri = URI("jar:" + path.toUri())
                    FileSystems.newFileSystem(uri, mapOf<String, Any?>()).use { fs ->
                        val innerPath = fs.rootDirectories.iterator().next().resolve("foo.txt")
                        println(Files.readString(innerPath))
                    }
                }
            }

            apply<SomePlugin>()
        """

        when:
        configurationCacheRun("readFileWithinJarTask")

        then:
        outputContains("bar")
    }

    def "reports build logic reading an environment value using #envVarRead.groovyExpression"() {
        buildFile << """
            println("CI = " + ${envVarRead.groovyExpression})
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        EnvVariableInjection.checkEnvironmentVariableUnset("CI")
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': environment variable 'CI'")
        }
        outputContains("CI = $notDefined")

        when:
        EnvVariableInjection.environmentVariable("CI", value).setup(this)
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': environment variable 'CI'")
        }
        outputContains("CI = $expectedValue")

        where:
        envVarRead                                          | notDefined | value     | expectedValue
        EnvVariableRead.getEnv("CI")                        | "null"     | "defined" | "defined"
        EnvVariableRead.getEnvGet("CI")                     | "null"     | "defined" | "defined"
        EnvVariableRead.getEnvGetOrDefault("CI", "default") | "default"  | "defined" | "defined"
        EnvVariableRead.getEnvContainsKey("CI")             | "false"    | "defined" | "true"
    }

    @Issue("https://github.com/gradle/gradle/issues/19710")
    def "modification of allowed properties does not invalidate cache"() {
        buildFile("""
            def oldValue = System.setProperty("java.awt.headless", "true")
            println("previous value = \$oldValue")

            // Attempt to capture the modified property value.
            println("configuration time value=\${System.getProperty("java.awt.headless")}")

            tasks.register("printProperty") {
                doLast {
                    println("execution time value=\${System.getProperty("java.awt.headless")}")
                }
            }
        """)
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("-Djava.awt.headless=false", "printProperty")

        then:
        configurationCache.assertStateStored()
        outputContains("configuration time value=true")
        outputContains("execution time value=true")

        when:
        configurationCacheRun("-Djava.awt.headless=false", "printProperty")

        then:
        configurationCache.assertStateLoaded()
        outputContains("execution time value=true")
    }

    def "reports build logic reading environment variables with getenv(String) using GString parameters"() {
        // Note that the map returned from System.getenv() doesn't support GStrings as keys, so there is no point in testing it.
        buildFile << '''
            def ci = "ci"
            def value = "value"
            println "CI1 = " + System.getenv("${ci.toUpperCase()}1")
        '''

        when:
        EnvVariableInjection.checkEnvironmentVariableUnset("CI1")
        configurationCacheRun()

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': environment variable 'CI1'")
        }
        outputContains("CI1 = null")

        when:
        EnvVariableInjection.environmentVariable("CI1", "defined").setup(this)
        configurationCacheRun()

        then:
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': environment variable 'CI1'")
        }
        outputContains("CI1 = defined")
    }

    def "build logic can read environment variables with prefix"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            def ciVars = providers.environmentVariablesPrefixedBy("CI")
            ciVars.get().forEach((k, v) -> {
                println("Configuration: \$k = \$v")
            })

            tasks.register("print") {
                doLast {
                    ciVars.get().forEach((k, v) -> {
                        println("Execution: \$k = \$v")
                    })
                }
            }
        """)

        when:
        EnvVariableInjection.environmentVariable("CI1", "1").setup(this)
        configurationCacheRun("print")

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration: CI1 = 1")
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': environment variables prefixed by 'CI'")
        }


        when:
        EnvVariableInjection.environmentVariable("CI1", "1").setup(this)
        configurationCacheRun("print")
        outputContains("Execution: CI1 = 1")

        then:
        configurationCache.assertStateLoaded()

        when:
        EnvVariableInjection.environmentVariables(CI1: "1", CI2: "2").setup(this)
        configurationCacheRun("print")

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration: CI1 = 1")
        outputContains("Configuration: CI2 = 2")
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': environment variables prefixed by 'CI'")
        }
    }

    def "build logic can read system properties with prefix"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            def ciVars = providers.systemPropertiesPrefixedBy("some.property.")
            ciVars.get().forEach((k, v) -> {
                println("Configuration: \$k = \$v")
            })

            tasks.register("print") {
                doLast {
                    ciVars.get().forEach((k, v) -> {
                        println("Execution: \$k = \$v")
                    })
                }
            }
        """)

        when:
        configurationCacheRun("-Dsome.property.1=1", "print")

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration: some.property.1 = 1")
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system properties prefixed by 'some.property.'")
        }

        when:
        configurationCacheRun("-Dsome.property.1=1", "print")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Execution: some.property.1 = 1")

        when:
        configurationCacheRun("-Dsome.property.1=1", "-Dsome.property.2=2", "print")

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration: some.property.1 = 1")
        outputContains("Configuration: some.property.2 = 2")
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system properties prefixed by 'some.property.'")
        }
    }

    def "build logic can read Gradle properties with prefix"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            def ciVars = providers.gradlePropertiesPrefixedBy("some.property.")
            ciVars.get().forEach((k, v) -> {
                println("Configuration: \$k = \$v")
            })

            tasks.register("print") {
                doLast {
                    ciVars.get().forEach((k, v) -> {
                        println("Execution: \$k = \$v")
                    })
                }
            }
        """)

        when:
        configurationCacheRun("-Psome.property.1=1", "-Dsome.property.2=2", "print")

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration: some.property.1 = 1")
        outputDoesNotContain("Configuration: some.property.2 = 2")
        problems.assertResultHasProblems(result) {
            withNoInputs()
        }

        when:
        configurationCacheRun("-Psome.property.1=1", "print")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Execution: some.property.1 = 1")

        when:
        configurationCacheRun("-Psome.property.1=1", "-Psome.property.2=2", "print")

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration: some.property.1 = 1")
        outputContains("Configuration: some.property.2 = 2")
        problems.assertResultHasProblems(result) {
            withNoInputs()
        }
    }

    def "system properties overwritten in build logic are not inputs to prefixed system properties"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            System.properties.putAll(("some.property.1"): "0")
            def ciVars = providers.systemPropertiesPrefixedBy("some.property.")
            ciVars.get().forEach((k, v) -> {
                println("Configuration: \$k = \$v")
            })

            tasks.register("print") {
                doLast {
                    ciVars.get().forEach((k, v) -> {
                        println("Execution: \$k = \$v")
                    })
                }
            }
        """)

        when:
        configurationCacheRun("-Dsome.property.1=1", "-Dsome.property.2=2", "print")

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration: some.property.1 = 0")
        outputContains("Configuration: some.property.2 = 2")

        when:
        configurationCacheRun("-Dsome.property.1=-1", "-Dsome.property.2=2", "print")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Execution: some.property.1 = 0")
        outputContains("Execution: some.property.2 = 2")
    }

    def "system properties overwritten in build logic cannot be overridden by CLI argument"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            System.properties.putAll(("someProperty"): "build-logic-value")
            def property = providers.systemProperty("someProperty")

             tasks.register("print") {
                doLast {
                 println("Execution: \${property.orNull}")
                }
             }
        """)

        when:
        configurationCacheRun "print"

        then:
        configurationCache.assertStateStored()
        outputContains("Execution: build-logic-value")

        when:
        System.clearProperty("someProperty")
        configurationCacheRun "print", "-DsomeProperty=cli-overridden-value"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Execution: build-logic-value")
    }

    def "reports build logic reading files in #title"() {
        def configurationCache = newConfigurationCacheFixture()
        def inputFile = testDirectory.file("testInput.txt") << "some test input"

        testDirectory.file(buildFileName) << code

        when:
        configurationCacheRun(":help")

        then: "initial run has no errors but detects input"
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withInput("Build file '$buildFileName': file 'testInput.txt'")
        }

        when:
        configurationCacheRun(":help")

        then: "without changes in file the cache is reused"
        configurationCache.assertStateLoaded()

        when:
        inputFile << "some other input"
        configurationCacheRun(":help")

        then: "changes in the file invalidate the cache"
        configurationCache.assertStateStored()

        where:
        title                                    | buildFileName      | code
        "Groovy with FileInputStream"            | "build.gradle"     | readFileWithFileInputStreamInGroovy()
        "Groovy with FileInputStream descendant" | "build.gradle"     | readFileWithFileInputStreamDescendantInGroovy()
        "Kotlin with FileInputStream"            | "build.gradle.kts" | readFileWithFileInputStreamInKotlin()
        "Kotlin with FileInputStream descendant" | "build.gradle.kts" | readFileWithFileInputStreamDescendantInKotlin()
    }

    def "reading file in buildSrc task is not tracked"() {
        def configurationCache = newConfigurationCacheFixture()
        testDirectory.file("buildSrc/testInput.txt") << "some test input"

        testDirectory.file("buildSrc/build.gradle") << """
            def inputFile = file("testInput.txt")
            def echoTask = tasks.register("echo") {
                doLast {
                    def fin = new FileInputStream(inputFile)
                    try {
                        System.out.bytes = fin.bytes
                    } finally {
                        fin.close()
                    }
                }
            }
            tasks.named("classes").configure {
                dependsOn(echoTask)
            }
        """

        buildFile << ""

        when:
        configurationCacheRun(":help")

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withNoInputs()
        }
        outputContains("some test input")
    }

    private static String readFileWithFileInputStreamInGroovy() {
        return """
            def fin = new FileInputStream(file("testInput.txt"))
            try {
                System.out.bytes = fin.bytes
            } finally {
                fin.close()
            }
        """
    }

    private static String readFileWithFileInputStreamDescendantInGroovy() {
        return """
            class TestInputStream extends FileInputStream {
                TestInputStream(String path) {
                    super(new File(path))
                }
            }

            def fin = new TestInputStream(file("testInput.txt").path)
            try {
                System.out.bytes = fin.bytes
            } finally {
                fin.close()
            }
        """
    }

    private static String readFileWithFileInputStreamInKotlin() {
        return """
            import java.io.*

            FileInputStream(file("testInput.txt")).use {
                it.copyTo(System.out)
            }
        """
    }

    private static String readFileWithFileInputStreamDescendantInKotlin() {
        return """
            import java.io.*

            class TestInputStream(path: String) : FileInputStream(file(path)) {}

            TestInputStream("testInput.txt").use {
                it.copyTo(System.out)
            }
        """
    }
}
