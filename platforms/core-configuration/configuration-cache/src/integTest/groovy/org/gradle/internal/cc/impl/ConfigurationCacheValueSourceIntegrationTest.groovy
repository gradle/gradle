/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.process.ShellScript
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class ConfigurationCacheValueSourceIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "value source without parameters can be used as task input"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile("""
            import org.gradle.api.provider.*

            abstract class GreetValueSource implements ValueSource<String, ValueSourceParameters.None> {
                String obtain() {
                    return "Hello!"
                }
            }

            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<String> getGreeting()

                @TaskAction void run() { println greeting.get() }
            }

            def greetValueSource = providers.of(GreetValueSource) {}
            tasks.register("greet", MyTask) {
                greeting = greetValueSource
            }
        """)

        when:
        configurationCacheRun "greet"

        then:
        configurationCache.assertStateStored()
        output.contains("Hello!")

        when:
        configurationCacheRun "greet"

        then:
        configurationCache.assertStateLoaded()
        output.contains("Hello!")
    }

    def "#usage property from properties file used as build logic input"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """

            import org.gradle.api.provider.*

            abstract class PropertyFromPropertiesFile : ValueSource<String, PropertyFromPropertiesFile.Parameters> {

                interface Parameters : ValueSourceParameters {

                    @get:InputFile
                    val propertiesFile: RegularFileProperty

                    @get:Input
                    val propertyName: Property<String>
                }

                override fun obtain(): String? = parameters.run {
                    propertiesFile.get().asFile.takeIf { it.isFile }?.inputStream()?.use {
                        java.util.Properties().apply { load(it) }
                    }?.get(propertyName.get()) as String?
                }
            }

            val isCi: Provider<String> = providers.of(PropertyFromPropertiesFile::class) {
                parameters {
                    propertiesFile.set(layout.projectDirectory.file("local.properties"))
                    propertyName.set("ci")
                }
            }

            if ($expression) {
                tasks.register("run") {
                    doLast { println("ON CI") }
                }
            } else {
                tasks.register("run") {
                    doLast { println("NOT CI") }
                }
            }
        """

        when: "running without a file present"
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when: "running with an empty file"
        file("local.properties") << ""
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateLoaded()

        when: "running with the property present in the file"
        file("local.properties") << "ci=true"
        configurationCacheRun "run"

        then:
        output.count("ON CI") == 1
        configurationCache.assertStateStored()

        when: "running after changing the file without changing the property value"
        file("local.properties") << "\nunrelated.properties=foo"
        configurationCacheRun "run"

        then:
        output.count("ON CI") == 1
        configurationCache.assertStateLoaded()

        when: "running after changing the property value"
        file("local.properties").text = "ci=false"
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        where:
        expression                                     | usage
        'isCi.map(String::toBoolean).getOrElse(false)' | 'mapped'
        'isCi.getOrElse("false") != "false"'           | 'raw'
    }

    def "can define and use custom value source in a Groovy script"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile.text = """

            import org.gradle.api.provider.*

            abstract class IsSystemPropertySet implements ValueSource<Boolean, Parameters> {
                interface Parameters extends ValueSourceParameters {
                    Property<String> getPropertyName()
                }
                @Override Boolean obtain() {
                    System.getProperties().get(parameters.getPropertyName().get()) != null
                }
            }

            def isCi = providers.of(IsSystemPropertySet) {
                parameters {
                    propertyName = "ci"
                }
            }
            if (isCi.get()) {
                tasks.register("build") {
                    doLast { println("ON CI") }
                }
            } else {
                tasks.register("build") {
                    doLast { println("NOT CI") }
                }
            }
        """

        when:
        configurationCacheRun "build"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "build"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "build", "-Dci=true"

        then:
        output.count("ON CI") == 1
        output.contains("because a build logic input of type 'IsSystemPropertySet' has changed")
        configurationCache.assertStateStored()
    }

    def "exception thrown from ValueSource becomes problem if the exception is #exceptionHandlerDescritpion"() {
        given:
        buildFile("""
            import org.gradle.api.provider.*

            abstract class BrokenValueSource implements ValueSource<String, ValueSourceParameters.None>, Describable {
                @Override String obtain() { throw new RuntimeException("Broken!") }
                @Override String getDisplayName() { "some name" }
            }

            try {
                providers.of(BrokenValueSource) {}.get()
            } catch (Throwable ex) {
                $exceptionHandlerImpl
            }
        """)

        when:
        configurationCacheFails()

        then:
        outputContains("Configuration cache entry discarded with 1 problem.")
        failure.assertHasFailures(expectedFailuresCount)
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount == 1
            problemsWithStackTraceCount == 1
            withProblem("Build file 'build.gradle': line 5: failed to compute value with custom source 'BrokenValueSource' (some name) with java.lang.RuntimeException: Broken!")
        }

        where:
        exceptionHandlerDescritpion | exceptionHandlerImpl | expectedFailuresCount
        "rethrown"                  | "throw ex"           | 2  // The original exception propagates and fails the build, and configuration cache problem is reported too.
        "ignored"                   | "// ignored"         | 1  // Only the configuration cache problem is reported
    }

    def "exception thrown from ValueSource when populating the cache invalidates the entry upon fingerprint check "() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            import org.gradle.api.provider.*

            abstract class BrokenValueSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override String obtain() { throw new RuntimeException("Broken!") }
            }

            try {
                providers.of(BrokenValueSource) {}.get()
            } catch (Throwable ignored) {
            }
        """)

        when:
        configurationCacheRunLenient()

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRunLenient()

        then:
        configurationCache.assertStateStored()
        outputContains("configuration cache cannot be reused because a build logic input of type 'BrokenValueSource' failed when storing the entry with java.lang.RuntimeException: Broken!.")
    }

    def "exception thrown from ValueSource when computing its value for fingerprint checking fails the build"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            import org.gradle.api.provider.*

            abstract class SometimesBrokenValueSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override String obtain() {
                    if (Boolean.getBoolean("should.fail")) {
                        throw new RuntimeException("Broken!")
                    }
                    return "not broken"
                }
            }

            providers.of(SometimesBrokenValueSource) {}.get()
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheFails("-Dshould.fail=true")

        then:
        failure.assertHasDescription("Broken!")
    }

    def "ValueSource can use #accessor without making it an input"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile.text = """

            import org.gradle.api.provider.*

            abstract class IsInputSet implements ValueSource<Boolean, Parameters> {
                interface Parameters extends ValueSourceParameters {
                    Property<String> getPropertyName()
                }
                @Override Boolean obtain() {
                    return ${accessor}(parameters.propertyName.get()) != null
                }
            }

            def isCi = providers.of(IsInputSet) {
                parameters {
                    propertyName = "ci"
                }
            }
            if (isCi.get()) {
                tasks.register("build") {
                    doLast { println("ON CI") }
                }
            } else {
                tasks.register("build") {
                    doLast { println("NOT CI") }
                }
            }
        """

        when:
        executer.withEnvironmentVars(ci: "1")
        configurationCacheRun("-Dci=1", "build")

        then:
        configurationCache.assertStateStored()
        outputContains("ON CI")

        when: "changing the value of the input doesn't invalidate cache"
        executer.withEnvironmentVars(ci: "2")
        configurationCacheRun("-Dci=2", "build")

        then:
        configurationCache.assertStateLoaded()
        outputContains("ON CI")

        when: "removing the input invalidates cache"
        configurationCacheRun("build")

        then:
        configurationCache.assertStateStored()
        outputContains("NOT CI")

        where:
        accessor             | _
        "System.getProperty" | _
        "System.getenv"      | _
    }

    def "other build inputs are still tracked after computing ValueSource"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile.text = """

            import org.gradle.api.provider.*

            abstract class ConstantSource implements ValueSource<Integer, ValueSourceParameters.None> {
                @Override Integer obtain() {
                    return 42
                }
            }

            def vsResult = providers.of(ConstantSource) {}
            println("ValueSource result = \${vsResult.get()}")
            println("some.property = \${System.getProperty("some.property")}")
        """

        when:
        configurationCacheRun("-Dsome.property=1")

        then:
        configurationCache.assertStateStored()
        outputContains("some.property = 1")
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system property 'some.property'")
            ignoringUnexpectedInputs()
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/22305")
    def "evaluating a value source parameter does not enable input tracking inside the value source"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile.text = """

            import org.gradle.api.provider.*

            abstract class MySource implements ValueSource<String, Parameters> {
                interface Parameters extends ValueSourceParameters {
                    Property<String> getValue()
                }
                @Override String obtain() {
                    def suffix = parameters.value.orElse("").get()
                    return System.getProperty("my.property") + suffix
                }
            }

            def someProp = providers.systemProperty("some.property")

            def vsResult = providers.of(MySource) {
                parameters.value = someProp
            }

            println("ValueSource result = \${vsResult.get()}")
        """

        when:
        configurationCacheRun("-Dsome.property=1", "-Dmy.property=value")

        then:
        configurationCache.assertStateStored()
        outputContains("result = value1")
        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': system property 'some.property'")
            withInput("Build file 'build.gradle': value from custom source 'MySource'")
        }
    }

    def "value source can use standard process API"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        ShellScript testScript = ShellScript.builder().printText("Hello, world").writeTo(testDirectory, "script")

        buildFile.text = """
            import ${ByteArrayOutputStream.name}
            import org.gradle.api.provider.*

            abstract class ProcessSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override String obtain() {
                    def baos = new ByteArrayOutputStream()
                    def process = ${ShellScript.cmdToStringLiteral(testScript.getRelativeCommandLine(testDirectory))}.execute()
                    process.waitForProcessOutput(baos, System.err)
                    return baos.toString().trim()
                }
            }

            def vsResult = providers.of(ProcessSource) {}
            println("ValueSource result = \${vsResult.get()}")
        """

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        outputContains("ValueSource result = Hello, world")
    }

    def "value source can read mutated system property inputs at configuration time"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
        import org.gradle.api.provider.*

        abstract class IdentitySource implements ValueSource<String, Parameters> {
            interface Parameters extends ValueSourceParameters {
                Property<String> getValue()
            }

            @Override String obtain() {
                return parameters.value.orNull
            }
        }

        def vsResult = providers.of(IdentitySource) {
            parameters.value = providers.systemProperty("property")
        }

        System.setProperty("property", "someValue")

        println("configuration value = \${vsResult.getOrElse("NO VALUE")}")

        tasks.register("echo") {
            doLast {
                println("execution value = \${vsResult.getOrElse("NO VALUE")}")
            }
        }

        defaultTasks "echo"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        outputContains("configuration value = someValue")
        outputContains("execution value = someValue")

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateLoaded()
        outputContains("execution value = someValue")
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/23689")
    def "value source can read mutated system property at configuration time with Java API"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
        import org.gradle.api.provider.*

        abstract class SystemPropSource implements ValueSource<String, ValueSourceParameters.None> {
            @Override String obtain() {
                return System.getProperty("property")
            }
        }

        def vsResult = providers.of(SystemPropSource) {}

        System.setProperty('property', 'someValue')

        println("configuration value = \${vsResult.getOrElse("NO VALUE")}")

        tasks.register("echo") {
            doLast {
                println("execution value = \${vsResult.getOrElse("NO VALUE")}")
            }
        }

        defaultTasks "echo"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        outputContains("configuration value = someValue")
        outputContains("execution value = someValue")

        when:
        configurationCacheRun()

        then:
        // TODO(mlopatkin) This behavior is correct but suboptimal, as we're never going to have a cache hit.
        //  We may want to warn the user about it.
        configurationCache.assertStateStored()
        outputContains("configuration value = someValue")
        outputContains("execution value = someValue")
    }

    def "value source changes its value invalidating the cache if its input #providerType provider changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
        import org.gradle.api.provider.*

        abstract class IdentitySource implements ValueSource<String, Parameters> {
            interface Parameters extends ValueSourceParameters {
                Property<String> getValue()
            }

            @Override String obtain() {
                return parameters.value.orNull
            }
        }

        def vsResult = providers.of(IdentitySource) {
            parameters.value = providers.$providerType("property")
        }

        println("configuration value = \${vsResult.getOrElse("NO VALUE")}")

        tasks.register("echo") {
            doLast {
                println("execution value = \${vsResult.getOrElse("NO VALUE")}")
            }
        }

        defaultTasks "echo"
        """)

        when:
        configurationCacheRun("${setterSwitch}property=someValue")

        then:
        configurationCache.assertStateStored()
        outputContains("configuration value = someValue")
        outputContains("execution value = someValue")

        when:
        configurationCacheRun("${setterSwitch}property=someValue")

        then:
        configurationCache.assertStateLoaded()
        outputContains("execution value = someValue")

        when:
        configurationCacheRun("${setterSwitch}property=newValue")

        then:
        configurationCache.assertStateStored()
        outputContains("configuration value = newValue")
        outputContains("execution value = newValue")

        where:
        providerType     | setterSwitch
        "systemProperty" | "-D"
        "gradleProperty" | "-P"
    }

    def "value source with non-serializable output"() {
        buildFile("""
        import org.gradle.api.provider.*

        abstract class BrokenSource implements ValueSource<Thread, ValueSourceParameters.None> {
            @Override Thread obtain() {
                return new Thread()
            }
        }

        providers.of(BrokenSource) {}.get()
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

    def 'reentrant fingerprint'() {
        def configurationCache = newConfigurationCacheFixture()
        given:
        buildFile """
            abstract class CustomValueSource implements ValueSource<String, Parameters> {
                interface Parameters extends ValueSourceParameters {
                    Property<String> getString()
                }
                @Override String obtain() {
                    return parameters.string.get()
                }
            }
            abstract class PrintTask extends DefaultTask {
                @Input abstract Property<String> getMessage()
                @TaskAction def doIt() {
                    println message.get()
                }
            }
            tasks.register('build', PrintTask) {
                message = providers.of(CustomValueSource) {
                    parameters {
                        string = provider {
                            System.getProperty('MY_SYSTEM_PROPERTY', '42')
                        }
                    }
                }.get() // explicitly calling get to trigger reentrant fingerprint behavior
            }
        """
        when:
        configurationCacheRun 'build'
        then:
        outputContains '42'
        configurationCache.assertStateStored()
        when:
        configurationCacheRun 'build'
        then:
        outputContains '42'
        configurationCache.assertStateLoaded()
        when:
        configurationCacheRun 'build', '-DMY_SYSTEM_PROPERTY=2001'
        then:
        outputContains '2001'
        configurationCache.assertStateStored()
    }

    def 'fingerprint does not block'() {
        given:
        buildFile """
            abstract class CustomValueSource implements ValueSource<String, Parameters> {
                interface Parameters extends ValueSourceParameters {
                    Property<String> getString()
                }
                @Override String obtain() {
                    return parameters.string.get()
                }
            }
            abstract class PrintTask extends DefaultTask {
                @Input abstract Property<String> getMessage()
                @TaskAction def doIt() {
                    println message.get()
                }
            }
            tasks.register('build', PrintTask) {
                message = providers.of(CustomValueSource) {
                    parameters {
                        string = provider {
                            String prop = null
                             def t = Thread.start {
                                prop = System.getProperty('MY_SYSTEM_PROPERTY', '42')
                            }
                            t.join(5_000)
                            if (t.isAlive()) {
                                println(t.getStackTrace().join("\\n\\t"))
                                throw new RuntimeException("deadlock")
                            }
                            prop
                        }
                    }
                }.get() // explicitly calling get to trigger reentrant fingerprint behavior
            }
        """
        when:
        configurationCacheRun 'build'
        then:
        outputContains '42'
        when:
        configurationCacheRun 'build'
        then:
        outputContains '42'
        when:
        configurationCacheRun 'build', "-DMY_SYSTEM_PROPERTY=2001"
        then:
        outputContains '2001'
    }

    @ToBeFixedForIsolatedProjects(because = 'ValueSource instances cannot be shared across projects')
    def "value source can be shared across projects"() {
        given:
        createDirs 'foo', 'bar'
        settingsFile """
            import org.gradle.api.provider.*

            include 'foo', 'bar'

            abstract class StringValueSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override String obtain() {
                    println "StringValueSource obtained"
                    new String('42')
                }
            }

            abstract class ValueCheckerService implements ${BuildService.name}<${BuildServiceParameters.name}.None>{

                private Object value = null

                synchronized def check(Object o) {
                    if (value === null) {
                        value = o
                    } else {
                        assert value === o
                        println 'The values are the same'
                    }
                }
            }

            abstract class ValueTask extends DefaultTask {
                @Input abstract Property<Object> getValue()
                @ServiceReference('valueChecker') abstract Property<ValueCheckerService> getService()
                @TaskAction def check() {
                    service.get().check(value.get())
                }
            }

            def service = gradle.sharedServices.registerIfAbsent('valueChecker', ValueCheckerService) {}

            def sharedValue = providers.of(StringValueSource) {}
            gradle.allprojects {
                tasks.register('check', ValueTask) {
                    value = sharedValue
                }
            }
        """

        and:
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'check'

        then:
        output.count('StringValueSource obtained') == 1
        output.count('The values are the same') == 2

        and:
        configurationCacheRun 'check'

        then:
        output.count('StringValueSource obtained') == 1
        output.count('The values are the same') == 2

        and:
        configurationCache.assertStateLoaded()
    }

    def "value source can be shared across build services"() {
        given:
        createDirs 'foo', 'bar'
        settingsFile """
            import org.gradle.api.provider.*

            rootProject.name = 'root'
            include 'foo', 'bar'

            abstract class StringValueSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override String obtain() {
                    println "StringValueSource obtained"
                    new String('42')
                }
            }

            abstract class ValueProviderService implements ${BuildService.name}<Parameters>{

                interface Parameters extends ${BuildServiceParameters.name} {
                    Property<Object> getValue()
                }

                Object getValue() {
                    parameters.value.get()
                }
            }

            abstract class ValueCheckerService implements ${BuildService.name}<${BuildServiceParameters.name}.None>{

                private Object value = null

                synchronized def check(Object o) {
                    if (value === null) {
                        value = o
                    } else {
                        assert value === o
                        println 'The values are the same'
                    }
                }
            }

            abstract class ValueTask extends DefaultTask {
                @ServiceReference('valueProvider') abstract Property<ValueProviderService> getValueProvider()
                @ServiceReference('valueChecker') abstract Property<ValueCheckerService> getValueChecker()
                @TaskAction def check() {
                    valueChecker.get().check(valueProvider.get().value)
                }
            }

            def valueChecker = gradle.sharedServices.registerIfAbsent('valueChecker', ValueCheckerService) {}

            def sharedValue = providers.of(StringValueSource) {}
            gradle.allprojects {
                def provider = gradle.sharedServices.registerIfAbsent(name + 'ValueProvider', ValueProviderService) {
                    parameters {
                        value = sharedValue
                    }
                }
                tasks.register('check', ValueTask) {
                    valueProvider = provider
                }
            }
        """

        and:
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'check'

        then:
        output.count('StringValueSource obtained') == 1
        output.count('The values are the same') == 2

        and:
        configurationCacheRun 'check'

        then:
        output.count('StringValueSource obtained') == 1
        output.count('The values are the same') == 2

        and:
        configurationCache.assertStateLoaded()
    }
}
