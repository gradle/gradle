/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache

import spock.lang.Unroll

class ConfigurationCacheBuildOptionsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Unroll
    def "system property from #systemPropertySource used as task and build logic input"() {

        given:
        def configurationCache = newConfigurationCacheFixture()
        createDir('root') {
            file('build.gradle.kts') << """

                $greetTask

                val greetingProp = providers.systemProperty("greeting").forUseAtConfigurationTime()
                if (greetingProp.get() == "hello") {
                    tasks.register<Greet>("greet") {
                        greeting.set("hello, hello")
                    }
                } else {
                    tasks.register<Greet>("greet") {
                        greeting.set(greetingProp)
                    }
                }
            """
        }
        def runGreetWith = { String greeting ->
            inDirectory('root')
            switch (systemPropertySource) {
                case SystemPropertySource.COMMAND_LINE:
                    return configurationCacheRun('greet', "-Dgreeting=$greeting")
                case SystemPropertySource.GRADLE_PROPERTIES:
                    file('root/gradle.properties').text = "systemProp.greeting=$greeting"
                    return configurationCacheRun('greet')
                case SystemPropertySource.GRADLE_PROPERTIES_FROM_MASTER_SETTINGS_DIR:
                    file('master/gradle.properties').text = "systemProp.greeting=$greeting"
                    file('master/settings.gradle').text = """
                        rootProject.projectDir = file('../root')
                    """
                    return configurationCacheRun('greet')
            }
            throw new IllegalArgumentException('source')
        }
        when:
        runGreetWith 'hi'

        then:
        output.count("Hi!") == 1
        configurationCache.assertStateStored()

        when:
        runGreetWith 'hi'

        then:
        output.count("Hi!") == 1
        configurationCache.assertStateLoaded()

        when:
        runGreetWith 'hello'

        then:
        output.count("Hello, hello!") == 1
        configurationCache.assertStateStored()

        where:
        systemPropertySource << SystemPropertySource.values()
    }

    enum SystemPropertySource {
        COMMAND_LINE,
        GRADLE_PROPERTIES,
        GRADLE_PROPERTIES_FROM_MASTER_SETTINGS_DIR;

        @Override
        String toString() {
            name().toLowerCase().replace('_', ' ')
        }
    }

    @Unroll
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
            }.forUseAtConfigurationTime()

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

    @Unroll
    def "#kind property used as task and build logic input"() {

        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """

            $greetTask

            val greetingProp = providers.${kind}Property("greeting").forUseAtConfigurationTime()
            if (greetingProp.get() == "hello") {
                tasks.register<Greet>("greet") {
                    greeting.set("hello, hello")
                }
            } else {
                tasks.register<Greet>("greet") {
                    greeting.set(greetingProp)
                }
            }
        """
        when:
        configurationCacheRun("greet", "-${option}greeting=hi")

        then:
        output.count("Hi!") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("greet", "-${option}greeting=hi")

        then:
        output.count("Hi!") == 1
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun("greet", "-${option}greeting=hello")

        then:
        output.count("Hello, hello!") == 1
        configurationCache.assertStateStored()
        outputContains "$description property 'greeting' has changed"

        where:
        kind     | option | description
        'system' | 'D'    | 'system'
        'gradle' | 'P'    | 'Gradle'
    }

    def "mapped system property used as task input"() {

        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """

            val sysPropProvider = providers
                .systemProperty("thread.pool.size")
                .map(Integer::valueOf)
                .orElse(1)

            abstract class TaskA : DefaultTask() {

                @get:Input
                abstract val threadPoolSize: Property<Int>

                @TaskAction
                fun act() {
                    println("ThreadPoolSize = " + threadPoolSize.get())
                }
            }

            tasks.register<TaskA>("a") {
                threadPoolSize.set(sysPropProvider)
            }
        """

        when:
        configurationCacheRun("a")

        then:
        output.count("ThreadPoolSize = 1") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("a", "-Dthread.pool.size=4")

        then:
        output.count("ThreadPoolSize = 4") == 1
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun("a", "-Dthread.pool.size=3")

        then:
        output.count("ThreadPoolSize = 3") == 1
        configurationCache.assertStateLoaded()
    }

    def "zipped properties used as task input"() {

        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """

            val prefix = providers.gradleProperty("messagePrefix")
            val suffix = providers.gradleProperty("messageSuffix")
            val zipped = prefix.zip(suffix) { p, s -> p + " " + s + "!" }

            abstract class PrintLn : DefaultTask() {

                @get:Input
                abstract val message: Property<String>

                @TaskAction
                fun act() { println(message.get()) }
            }

            tasks.register<PrintLn>("ok") {
                message.set(zipped)
            }
        """

        when:
        configurationCacheRun("ok", "-PmessagePrefix=fizz", "-PmessageSuffix=buzz")

        then:
        output.count("fizz buzz!") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("ok", "-PmessagePrefix=foo", "-PmessageSuffix=bar")

        then:
        output.count("foo bar!") == 1
        configurationCache.assertStateLoaded()
    }

    @Unroll
    def "system property #usage used as build logic input"() {

        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """
            val isCi = providers.systemProperty("ci").forUseAtConfigurationTime()
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

        when:
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "run", "-Dci=true"

        then:
        output.count("ON CI") == 1
        configurationCache.assertStateStored()

        where:
        expression                                     | usage
        "isCi.map(String::toBoolean).getOrElse(false)" | "value"
        "isCi.isPresent"                               | "presence"
    }

    def "environment variable used as task and build logic input"() {

        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """

            $greetTask

            val greetingVar = providers.environmentVariable("GREETING")
            if (greetingVar.forUseAtConfigurationTime().get().startsWith("hello")) {
                tasks.register<Greet>("greet") {
                    greeting.set("hello, hello")
                }
            } else {
                tasks.register<Greet>("greet") {
                    greeting.set(greetingVar)
                }
            }
        """
        when:
        withEnvironmentVars(GREETING: "hi")
        configurationCacheRun("greet")

        then:
        output.count("Hi!") == 1
        configurationCache.assertStateStored()

        when:
        withEnvironmentVars(GREETING: "hi")
        configurationCacheRun("greet")

        then:
        output.count("Hi!") == 1
        configurationCache.assertStateLoaded()

        when:
        withEnvironmentVars(GREETING: "hello")
        configurationCacheRun("greet")

        then:
        output.count("Hello, hello!") == 1
        outputContains "environment variable 'GREETING' has changed"
        configurationCache.assertStateStored()
    }

    @Unroll
    def "file contents #usage used as build logic input"() {

        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """
            val ciFile = layout.projectDirectory.file("ci")
            val isCi = providers.fileContents(ciFile)
            if (isCi.$expression) {
                tasks.register("run") {
                    doLast { println("ON CI") }
                }
            } else {
                tasks.register("run") {
                    doLast { println("NOT CI") }
                }
            }
        """

        when:
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateLoaded()

        when:
        file("ci").text = "true"
        configurationCacheRun "run"

        then:
        output.count("ON CI") == 1
        configurationCache.assertStateStored()

        when: "file is touched but unchanged"
        file("ci").text = "true"
        configurationCacheRun "run"

        then: "cache is still valid"
        output.count("ON CI") == 1
        configurationCache.assertStateLoaded()

        when: "file is changed"
        file("ci").text = "false"
        configurationCacheRun "run"

        then: "cache is NO longer valid"
        output.count(usage.endsWith("presence") ? "ON CI" : "NOT CI") == 1
        outputContains "file 'ci' has changed"
        configurationCache.assertStateStored()

        where:
        expression                                                                            | usage
        "asText.forUseAtConfigurationTime().map(String::toBoolean).getOrElse(false)"          | "text"
        "asText.forUseAtConfigurationTime().isPresent"                                        | "text presence"
        "asBytes.forUseAtConfigurationTime().map { String(it).toBoolean() }.getOrElse(false)" | "bytes"
        "asBytes.forUseAtConfigurationTime().isPresent"                                       | "bytes presence"
    }

    def "mapped file contents used as task input"() {

        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """

            val threadPoolSizeProvider = providers
                .fileContents(layout.projectDirectory.file("thread.pool.size"))
                .asText
                .map(Integer::valueOf)

            abstract class TaskA : DefaultTask() {

                @get:Input
                abstract val threadPoolSize: Property<Int>

                @TaskAction
                fun act() {
                    println("ThreadPoolSize = " + threadPoolSize.get())
                }
            }

            tasks.register<TaskA>("a") {
                threadPoolSize.set(threadPoolSizeProvider)
            }
        """

        when:
        file("thread.pool.size").text = "4"
        configurationCacheRun("a")

        then:
        output.count("ThreadPoolSize = 4") == 1
        configurationCache.assertStateStored()

        when: "the file is changed"
        file("thread.pool.size").text = "3"
        configurationCacheRun("a")

        then: "the configuration cache is NOT invalidated"
        output.count("ThreadPoolSize = 3") == 1
        configurationCache.assertStateLoaded()
    }

    @Unroll
    def "file contents provider used as #usage has no value when underlying file provider has no value"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """

            $greetTask

            val emptyFileProperty = objects.fileProperty()
            val fileContents = providers.fileContents(emptyFileProperty).asText
            val greetingFromFile: $operatorType = fileContents.$operator("hello")
            tasks.register<Greet>("greet") {
                greeting.set(greetingFromFile)
            }
        """

        when:
        configurationCacheRun("greet")

        then:
        output.count("Hello!") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("greet")

        then:
        output.count("Hello!") == 1
        configurationCache.assertStateLoaded()

        where:
        operator                                | operatorType       | usage
        "forUseAtConfigurationTime().getOrElse" | "String"           | "build logic input"
        "orElse"                                | "Provider<String>" | "task input"
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
            if (isCi.forUseAtConfigurationTime().get()) {
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

    def "mapped gradleProperty in producer task"() {
        given:
        buildFile '''

            import java.nio.file.Files

            abstract class MyTask extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @Input
                abstract Property<Integer> getInputCount()

                @TaskAction
                void doTask() {
                    File outputFile = getOutputDir().get().asFile
                    outputFile.deleteDir()
                    outputFile.mkdirs()
                    for (int i = 0; i < getInputCount().get(); i++) {
                        new File(outputFile, i.toString()).mkdirs()
                    }
                }
            }

            abstract class ConsumerTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getMyInputs()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doTask() {
                    File outputFile = getOutputFile().get().asFile
                    outputFile.delete()
                    outputFile.parentFile.mkdirs()
                    String outputContent = ""
                    for(File f: getMyInputs().files) {
                        outputContent += f.canonicalPath + "\\n"
                    }
                    Files.write(outputFile.toPath(), outputContent.getBytes())
                }
            }

            tasks.register("myTask", MyTask.class) {
                it.getInputCount().set(project.providers.gradleProperty("generateInputs").map { Integer.parseInt(it) })
                it.getOutputDir().set(new File(project.buildDir, "mytask"))
            }

            tasks.register("consumer", ConsumerTask.class) {
                it.getOutputFile().set(new File(project.buildDir, "consumer/out.txt"))

                MyTask myTask = (MyTask) tasks.getByName("myTask")
                Provider<Set<File>> inputs = myTask.outputDir.map {
                    File[] files = it.asFile.listFiles()
                    Set<File> result = new HashSet<File>()
                    for (File f : files) {
                        if (f.name.toInteger() % 2 == 0) {
                            result.add(f)
                        }
                    }
                    System.err.println("Computing task inputs for consumer")
                    return result
                }

                it.getMyInputs().from(inputs)
            }
        '''
        def configurationCache = newConfigurationCacheFixture()
        def consumedFileNames = {
            file('build/consumer/out.txt').readLines().collect {
                new File(it).name
            }.toSet()
        }

        when:
        configurationCacheRun('consumer', '-PgenerateInputs=4')

        then:
        consumedFileNames() == ['0', '2'] as Set
        configurationCache.assertStateStored()

        when:
        configurationCacheRun('consumer', '-PgenerateInputs=6')

        then:
        consumedFileNames() == ['0', '2', '4'] as Set
        configurationCache.assertStateLoaded()
    }

    def "system property used at configuration time can be captured by task"() {
        given:
        buildFile """
            def sysProp = providers.systemProperty("some.prop").forUseAtConfigurationTime()
            println('sys prop value at configuration time = ' + sysProp.orNull)

            task ok {
                doLast {
                    println('sys prop value at execution time = ' + sysProp.orNull)
                }
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'ok', '-Dsome.prop=42'

        then:
        outputContains 'sys prop value at configuration time = 42'
        outputContains 'sys prop value at execution time = 42'
        configurationCache.assertStateStored()

        when:
        configurationCacheRun 'ok', '-Dsome.prop=42'

        then:
        outputDoesNotContain 'sys prop value at configuration time = 42'
        outputContains 'sys prop value at execution time = 42'
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun 'ok', '-Dsome.prop=37'

        then:
        outputContains 'sys prop value at configuration time = 37'
        outputContains 'sys prop value at execution time = 37'
        configurationCache.assertStateStored()
    }

    private static String getGreetTask() {
        """
            abstract class Greet : DefaultTask() {

                @get:Input
                abstract val greeting: Property<String>

                @TaskAction
                fun greet() {
                    println(greeting.get().capitalize() + "!")
                }
            }
        """.stripIndent()
    }

    private void withEnvironmentVars(Map<String, String> environment) {
        executer.withEnvironmentVars(environment)
    }
}
