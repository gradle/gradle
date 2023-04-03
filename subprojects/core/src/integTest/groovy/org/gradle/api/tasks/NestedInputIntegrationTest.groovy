/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class NestedInputIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, ValidationMessageChecker {

    def setup() {
        expectReindentedValidationMessage()
    }

    def "nested #type.simpleName input adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }

            class NestedBeanWithInput {
                @Input${kind}
                @PathSensitive(PathSensitivity.NONE)
                final ${type.name} input

                NestedBeanWithInput(${type.name} input) {
                    this.input = input
                }
            }

            class GeneratorTask extends DefaultTask {
                @Output${kind}
                final ${type.name} output = project.objects.${factory}()

                @TaskAction
                void doStuff() {
                    output${generatorAction}
                }
            }

            task generator(type: GeneratorTask) {
                output.set(project.layout.buildDirectory.${lookup}('output'))
            }

            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(project.objects.${factory}())
                bean.input.set(generator.output)
            }
        """

        when:
        run 'consumer'
        then:
        executedAndNotSkipped(':generator', ':consumer')

        where:
        kind        | type                | factory             | lookup | generatorAction
        'File'      | RegularFileProperty | 'fileProperty'      | 'file' | '.getAsFile().get().text = "Hello"'
        'Directory' | DirectoryProperty   | 'directoryProperty' | 'dir'  | '''.file('output.txt').get().getAsFile().text = "Hello"'''
    }

    def "nested FileCollection input adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }

            class NestedBeanWithInput {
                @InputFiles
                FileCollection input
            }

            class GeneratorTask extends DefaultTask {
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void doStuff() {
                    outputFile.getAsFile().get().text = "Hello"
                }
            }

            task generator(type: GeneratorTask) {
                outputFile = project.layout.buildDirectory.file('output')
            }

            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(input: files(generator.outputFile))
            }
        """

        when:
        run 'consumer'
        then:
        executedAndNotSkipped(':generator', ':consumer')
    }

    @Issue("https://github.com/gradle/gradle/issues/3811")
    def "nested input using output file property of different task adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }

            class NestedBeanWithInput {
                @InputFile
                final RegularFileProperty file

                NestedBeanWithInput(RegularFileProperty file) {
                    this.file = file
                }
            }

            class GeneratorTask extends DefaultTask {
                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void doStuff() {
                    outputFile.getAsFile().get().text = "Hello"
                }
            }

            task generator(type: GeneratorTask) {
                outputFile = project.layout.buildDirectory.file('output')
            }

            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(generator.outputFile)
            }
        """

        when:
        run 'consumer'
        then:
        executedAndNotSkipped(':generator')
        executedAndNotSkipped(':consumer')
    }

    @UnsupportedWithConfigurationCache(because = "task references another task")
    def "re-configuring #change in nested bean during execution time is detected"() {
        def fixture = new NestedBeanTestFixture()

        buildFile << fixture.taskWithNestedProperty()
        buildFile << """
            task configureTask {
                doLast {
                    taskWithNestedProperty.bean = secondBean
                }
            }

            taskWithNestedProperty.dependsOn(configureTask)
        """

        fixture.prepareInputFiles()

        when:
        fixture.runTask()
        then:
        executedAndNotSkipped(fixture.task)

        when:
        fixture.changeFirstBean(change)
        fixture.runTask()
        then:
        skipped(fixture.task)

        when:
        fixture.changeSecondBean(change)
        fixture.runTask()
        then:
        executedAndNotSkipped(fixture.task)

        where:
        change << ['inputProperty', 'inputFile', 'outputFile']
    }

    @UnsupportedWithConfigurationCache(because = "task references another task")
    def "re-configuring a nested bean from #from to #to during execution time is detected"() {
        def fixture = new NestedBeanTestFixture()

        buildFile << fixture.taskWithNestedProperty()
        buildFile << """
            taskWithNestedProperty.bean = ${from}

            task configureTask {
                doLast {
                    taskWithNestedProperty.bean = ${to}
                }
            }

            taskWithNestedProperty.dependsOn(configureTask)
        """

        fixture.prepareInputFiles()

        when:
        fixture.runTask()
        then:
        executedAndNotSkipped(fixture.task)

        when:
        fixture.changeFirstBean('inputProperty')
        fixture.runTask()
        then:
        if (to == 'null') {
            skipped(fixture.task)
        } else {
            executedAndNotSkipped(fixture.task)
        }

        where:
        from        | to
        'firstBean' | 'null'
        'null'      | 'firstBean'
    }

    def "re-configuring #change in nested bean after the task started executing has no effect"() {
        def fixture = new NestedBeanTestFixture()
        fixture.prepareInputFiles()
        buildFile << fixture.taskWithNestedProperty()
        buildFile << """
            taskWithNestedProperty.doLast {
                bean = secondBean
            }
        """

        when:
        fixture.runTask()
        then:
        executedAndNotSkipped(fixture.task)

        when:
        fixture.changeFirstBean(change)
        fixture.runTask()
        then:
        executedAndNotSkipped(fixture.task)

        when:
        fixture.changeSecondBean(change)
        fixture.runTask()
        then:
        skipped(fixture.task)

        where:
        change << ['inputProperty', 'inputFile', 'outputFile']
    }

    def "re-configuring a nested bean from #from to #to after the task started executing has no effect"() {
        def fixture = new NestedBeanTestFixture()
        fixture.prepareInputFiles()
        buildFile << fixture.taskWithNestedProperty()
        buildFile << """
            taskWithNestedProperty.bean = ${from}

            taskWithNestedProperty.doLast {
                bean = ${to}
            }
        """

        when:
        fixture.runTask()
        then:
        executedAndNotSkipped(fixture.task)

        when:
        fixture.changeFirstBean('inputProperty')
        fixture.runTask()
        then:
        if (from == 'null') {
            skipped(fixture.task)
        } else {
            executedAndNotSkipped(fixture.task)
        }

        where:
        from        | to
        'firstBean' | 'null'
        'null'      | 'firstBean'
    }

    class NestedBeanTestFixture {
        def firstInputFile = 'firstInput.txt'
        def firstOutputFile = 'build/firstOutput.txt'
        def secondInputFile = 'secondInput.txt'
        def secondOutputFile = 'build/secondOutput.txt'

        def task = ':taskWithNestedProperty'

        def inputProperties = [
            first: 'first',
            second: 'second'
        ]
        def inputFiles = [
            first: file(firstInputFile),
            second: file(secondInputFile)
        ]
        def outputFiles = [
            first: file(firstOutputFile),
            second: file(secondOutputFile)
        ]

        def changes = [
            inputProperty: { String property ->
                inputProperties[property] = inputProperties[property] + ' changed'
            },
            inputFile: { String property ->
                inputFiles[property] << ' changed'
            },
            outputFile: { String property ->
                outputFiles[property] << ' changed'
            }
        ]

        def changeFirstBean(String change) {
            changes[change]('first')
        }

        def changeSecondBean(String change) {
            changes[change]('second')
        }

        def prepareInputFiles() {
            file(firstInputFile).text = "first input file"
            file(secondInputFile).text = "second input file"
        }

        def runTask() {
            result = executer.withTasks(task, '-PfirstInput=' + inputProperties.first, '-PsecondInput=' + inputProperties.second).run()
        }

        String taskWithNestedProperty() {
            """
            class TaskWithNestedProperty extends DefaultTask {
                @Nested
                @Optional
                Object bean

                @OutputFile
                final RegularFileProperty outputFile = project.objects.fileProperty()

                @TaskAction
                void writeInputToFile() {
                    outputFile.getAsFile().get().text = bean == null ? 'null' : bean.toString()
                    if (bean != null) {
                        bean.doStuff()
                    }
                }
            }

            class NestedBean {
                @Input
                String firstInput

                @InputFile
                File firstInputFile

                @OutputFile
                File firstOutputFile

                String toString() {
                    firstInput
                }

                void doStuff() {
                    firstOutputFile.text = firstInputFile.text
                }
            }

            class OtherNestedBean {
                @Input
                String secondInput

                @InputFile
                File secondInputFile

                @OutputFile
                File secondOutputFile

                String toString() {
                    secondInput
                }

                void doStuff() {
                    secondOutputFile.text = secondInputFile.text
                }
            }

            def firstString = providers.gradleProperty('firstInput').orNull
            def firstBean = new NestedBean(firstInput: firstString, firstOutputFile: file("${firstOutputFile}"), firstInputFile: file("${firstInputFile}"))

            def secondString = providers.gradleProperty('secondInput').orNull
            def secondBean = new OtherNestedBean(secondInput: secondString, secondOutputFile: file("${secondOutputFile}"), secondInputFile: file("${secondInputFile}"))

            task taskWithNestedProperty(type: TaskWithNestedProperty) {
                bean = firstBean
                outputFile.set(project.layout.buildDirectory.file('output.txt'))
            }
        """
        }
    }

    def "execution fails when a nested property throws an exception where property is a #description"() {
        buildFile << """
            import javax.inject.Inject
            import org.gradle.api.provider.ProviderFactory

            abstract class TaskWithFailingNestedInput extends DefaultTask {
                @Inject
                abstract ProviderFactory getProviders()

                @Nested
                Object getNested() {
                    $propertyValue
                }

                @Input
                String input = "Hello"

                @OutputFile
                File outputFile

                @TaskAction
                void doStuff() {
                    outputFile.text = input
                }
            }

            task myTask(type: TaskWithFailingNestedInput) {
                outputFile = file('build/output.txt')
            }
        """

        expect:
        fails "myTask"
        failure.assertHasDescription("Execution failed for task ':myTask'.")
        failure.assertHasCause("BOOM")

        where:
        description                | propertyValue
        "Java type"                | "throw new RuntimeException(\"BOOM\")"
        "Provider"                 | "return providers.provider { throw new RuntimeException(\"BOOM\") }"
        "Provider in a collection" | "return [providers.provider { throw new RuntimeException(\"BOOM\") }]"

    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def "null on nested bean is validated #description"() {
        buildFile << """
            class TaskWithAbsentNestedInput extends DefaultTask {
                @Nested
                $property

                @Input
                String input = "Hello"

                @OutputFile
                File outputFile

                @TaskAction
                void doStuff() {
                    outputFile.text = input
                }
            }

            task myTask(type: TaskWithAbsentNestedInput) {
                outputFile = file('build/output.txt')
            }
        """

        expect:
        fails "myTask"
        failure.assertHasDescription("A problem was found with the configuration of task ':myTask' (type 'TaskWithAbsentNestedInput').")
        failureDescriptionContains(missingValueMessage { type('TaskWithAbsentNestedInput').property('nested') })

        where:
        description               | property
        "for plain Java property" | "Object nested"
        "for Provider property"   | "Provider<Object> nested = project.providers.provider { null }"
    }

    def "null on optional nested bean is allowed #description"() {
        buildFile << """
            class TaskWithAbsentNestedInput extends DefaultTask {
                @Nested
                @Optional
                $property

                @Input
                String input = "Hello"

                @OutputFile
                File outputFile

                @TaskAction
                void doStuff() {
                    outputFile.text = input
                }
            }

            task myTask(type: TaskWithAbsentNestedInput) {
                outputFile = file('build/output.txt')
            }
        """

        expect:
        succeeds "myTask"

        where:
        description               | property
        "for plain Java property" | "Object nested"
        "for Provider property"   | "Provider<Object> nested = project.providers.provider { null }"
    }

    def "changes to nested bean implementation are detected"() {
        buildFile << """
            class TaskWithNestedInput extends DefaultTask {
                @Nested
                Object nested

                @OutputFile
                File outputFile

                @TaskAction
                void doStuff() {
                    outputFile.text = nested.input
                }
            }

            class NestedBean {
                @Input
                input
            }

            class OtherNestedBean {
                @Input
                input
            }

            boolean useOther = providers.gradleProperty('useOther').present

            task myTask(type: TaskWithNestedInput) {
                outputFile = file('build/output.txt')
                nested = useOther ? new OtherNestedBean(input: 'string') : new NestedBean(input: 'string')
            }
        """

        def task = ':myTask'

        when:
        run task
        then:
        executedAndNotSkipped(task)

        when:
        run task
        then:
        skipped task

        when:
        run task, '-PuseOther=true'
        then:
        executedAndNotSkipped task
    }

    def "elements of nested iterable cannot be #description"() {
        buildFile << """
            class TaskWithNestedIterable extends DefaultTask {
                @Nested
                @Optional
                Iterable<Object> beans
            }

            class NestedBean {
                @Input
                String input
            }

            task myTask(type: TaskWithNestedIterable) {
                beans = [new NestedBean(input: 'input'), $elementValue]
            }
        """

        expect:
        fails 'myTask'
        failure.assertHasCause('Null value is not allowed for the nested collection property \'beans.$1\'')

        where:
        description | elementValue
        "null"      | "null"
        "absent"    | "project.providers.provider { null }"
    }

    def "nested iterable beans can be iterables themselves"() {
        buildFile << nestedBeanWithStringInput()
        buildFile << """
            class TaskWithNestedIterable extends DefaultTask {
                @Nested
                Iterable<Object> beans

                @OutputFile
                File outputFile

                @TaskAction
                void doStuff() {
                    outputFile.text = beans.flatten()*.input.join('\\n')
                }
            }

            def inputString = providers.gradleProperty('input').getOrElse('input')

            task myTask(type: TaskWithNestedIterable) {
                outputFile = file('build/output.txt')
                beans = [[new NestedBean(inputString)], [new NestedBean('secondInput')]]
            }
        """
        def task = ':myTask'

        when:
        run task
        then:
        executedAndNotSkipped task

        when:
        run task
        then:
        skipped task

        when:
        run task, '-Pinput=changed'
        then:
        executedAndNotSkipped task
    }

    def "recursive nested bean causes build to fail"() {
        buildFile << """
            class TaskWithNestedInput extends DefaultTask {
                @Nested
                Object nested

                @Input
                String input = "Hello"

                @OutputFile
                File outputFile

                @TaskAction
                void doStuff() {
                    outputFile.text = input
                }
            }

            class NestedBean {
                @Nested
                NestedBean nested
            }

            task myTask(type: TaskWithNestedInput) {
                outputFile = file('build/output.txt')
                nested = new NestedBean()
                nested.nested = nested
            }
        """

        expect:
        fails "myTask"
        failure.assertHasDescription("Could not determine the dependencies of task ':myTask'.")
        failure.assertHasCause("Cycles between nested beans are not allowed. Cycle detected between: 'nested' and 'nested.nested'.")
    }

    def "duplicate names in nested iterable are allowed"() {
        buildFile << taskWithNestedInput()
        buildFile << namedBeanClass()
        buildFile << """
            myTask.nested = [new NamedBean('name', 'value1'), new NamedBean('name', 'value2')]
        """

        expect:
        succeeds "myTask"
    }

    def "nested Provider is unpacked"() {
        buildFile << taskWithNestedInput()
        buildFile << nestedBeanWithStringInput()
        buildFile << """
            myTask.nested = provider { new NestedBean(providers.gradleProperty('input').get()) }
        """

        def myTask = ':myTask'
        when:
        run myTask, '-Pinput=original'
        then:
        executedAndNotSkipped myTask

        when:
        run myTask, '-Pinput=original'
        then:
        skipped myTask

        when:
        run myTask, '-Pinput=changed', '--info'
        then:
        executedAndNotSkipped myTask
        output.contains "Value of input property 'nested.input' has changed for task ':myTask'"
    }

    def "input changes for task with named nested beans"() {
        buildFile << taskWithNestedInput()
        buildFile << namedBeanClass()
        buildFile << """
            myTask.nested = [new NamedBean(providers.gradleProperty('namedName').get(), 'value1'), new NamedBean('name', 'value2')]
        """
        def taskPath = ':myTask'

        when:
        run taskPath, '-PnamedName=name1'
        then:
        executedAndNotSkipped taskPath

        when:
        run taskPath, '-PnamedName=name1'
        then:
        skipped taskPath

        when:
        run taskPath, '-PnamedName=different', '--info'
        then:
        executedAndNotSkipped taskPath
        output.contains("Input property 'nested.different\$0' has been added for task ':myTask'")
        output.contains("Input property 'nested.name1\$0' has been removed for task ':myTask'")
    }

    def "input changes for task with nested map"() {
        buildFile << taskWithNestedInput()
        buildFile << nestedBeanWithStringInput()
        buildFile << """
            myTask.nested = [(providers.gradleProperty('key').get()): new NestedBean('value1'), key2: new NestedBean('value2')]
        """
        def taskPath = ':myTask'

        when:
        run taskPath, '-Pkey=key1'
        then:
        executedAndNotSkipped taskPath

        when:
        run taskPath, '-Pkey=key1'
        then:
        skipped taskPath

        when:
        run taskPath, '-Pkey=different', '--info'
        then:
        executedAndNotSkipped taskPath
        output.contains("Input property 'nested.different' has been added for task ':myTask'")
        output.contains("Input property 'nested.key1' has been removed for task ':myTask'")
    }

    @Issue("https://github.com/gradle/gradle/issues/24594")
    def "nested map with non-string key works"() {
        buildFile << """
            abstract class CustomTask extends DefaultTask {
                @Nested
                abstract MapProperty<Integer, Object> getLazyMap()

                @Nested
                Map<Integer, Object> eagerMap = [:]

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void execute() {
                    outputFile.getAsFile().get() << lazyMap.get()
                    outputFile.getAsFile().get() << eagerMap
                }
            }

            tasks.register("customTask", CustomTask) {
                lazyMap.put(100, "example")
                eagerMap.put(100, "example")
                outputFile = file("output.txt")
            }
        """

        expect:
        succeeds("customTask")
        file("output.txt").text == "[100:example][100:example]"
    }


    private static String namedBeanClass() {
        """
            class NamedBean implements Named {
                @Internal final String name
                @Input final String value

                NamedBean(name, value) {
                    this.name = name
                    this.value = value
                }
            }
        """
    }

    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    def "task with nested bean loaded with custom classloader disables execution optimizations"() {
        file("input.txt").text = "data"
        buildFile << taskWithNestedBeanFromCustomClassLoader()

        when:
        fails "customTask"
        then:
        executedAndNotSkipped ":customTask"
        failureDescriptionStartsWith("A problem was found with the configuration of task ':customTask' (type 'TaskWithNestedProperty').")
        failureDescriptionContains(implementationUnknown {
            nestedProperty('bean')
            unknownClassloader('NestedBean')
        })
    }

    def "changes to nested domain object container are tracked"() {
        buildFile << taskWithNestedInput()
        buildFile << """
            abstract class Bean {
                @Internal
                final String name
                @Input
                abstract Property<String> getProp()

                Bean(String name) {
                    this.name = name
                }
            }
        """
        buildFile << """
            def domainObjectCollection = objects.domainObjectContainer(Bean)
            myTask.nested = domainObjectCollection

            domainObjectCollection.create('first') { prop = providers.gradleProperty('value').get() }
            domainObjectCollection.create('second') { prop = '2' }
        """

        when:
        run "myTask", "-Pvalue=1"
        then:
        executedAndNotSkipped(":myTask")

        when:
        run "myTask", "-Pvalue=1"
        then:
        skipped(":myTask")

        when:
        run "myTask", "-Pvalue=2", "--info"
        then:
        executedAndNotSkipped(":myTask")
        outputContains("Value of input property 'nested.\$0.prop' has changed for task ':myTask'")
    }

    private static String taskWithNestedBeanFromCustomClassLoader() {
        """
            @CacheableTask
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
                @TaskAction action() {
                    bean.output.text = bean.input.text
                }
            }

            def NestedBean = new GroovyClassLoader(getClass().getClassLoader()).parseClass '''
                import org.gradle.api.tasks.*

                class NestedBean {
                    @InputFile @PathSensitive(PathSensitivity.NONE) File input
                    @OutputFile File output
                }
            '''

            task customTask(type: TaskWithNestedProperty) {
                bean = NestedBean.getConstructor().newInstance()
                bean.input = file("input.txt")
                bean.output = file("build/output.txt")
            }
        """
    }

    private static String taskWithNestedInput() {
        """
            class TaskWithNestedInput extends DefaultTask {
                @Nested
                Object nested

                @Input
                String input = "Hello"

                @OutputFile
                File outputFile

                @TaskAction
                void doStuff() {
                    outputFile.text = input
                }
            }

            task myTask(type: TaskWithNestedInput) {
                outputFile = file('build/output.txt')
            }
        """
    }

    private static String nestedBeanWithStringInput() {
        """
            class NestedBean {
                @Input final String input

                NestedBean(String input) {
                    this.input = input
                }
            }
        """
    }

    def "implementation of nested closure in decorated bean is tracked"() {
        taskWithNestedBeanWithAction()
        buildFile << """
            extensions.create("bean", NestedBeanWithAction.class)

            bean {
                withAction { it.text = "hello" }
            }

            task myTask(type: TaskWithNestedBeanWithAction) {
                bean = project.bean
            }
        """

        buildFile.makeOlder()

        when:
        run 'myTask'

        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "hello"

        when:
        buildFile.text = buildFile.text.replace('it.text = "hello"', 'it.text = "changed"')
        run 'myTask', '--info'

        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
        output.contains "Implementation of input property 'bean.action' has changed for task ':myTask'"
    }

    @Issue("https://github.com/gradle/gradle/issues/11703")
    def "nested bean from closure can be used with the build cache"() {
        def project1 = file("project1").createDir()
        def project2 = file("project2").createDir()
        [project1, project2].each { projectDir ->
            taskWithNestedBeanWithAction(projectDir)
            def buildFile = projectDir.file("build.gradle")
            buildFile << """
                apply plugin: 'base'

                extensions.create("bean", NestedBeanWithAction.class)

                bean {
                    withAction { it.text = "hello" }
                }

                task myTask(type: TaskWithNestedBeanWithAction) {
                    bean = project.bean
                    outputs.cacheIf { true }
                }
            """
            buildFile.makeOlder()
            projectDir.file("settings.gradle") << localCacheConfiguration()
        }

        when:
        executer.inDirectory(project1)
        withBuildCache().run 'myTask'

        then:
        executedAndNotSkipped(':myTask')
        project1.file('build/tmp/myTask/output.txt').text == "hello"

        when:
        executer.inDirectory(project2)
        withBuildCache().run 'myTask'

        then:
        skipped(':myTask')
        project2.file('build/tmp/myTask/output.txt').text == "hello"
    }

    private TestFile nestedBeanWithAction(TestFile projectDir = temporaryFolder.testDirectory) {
        return projectDir.file("buildSrc/src/main/java/NestedBeanWithAction.java") << """
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.Action;
            import java.io.File;

            public class NestedBeanWithAction {
                private Action<File> action;

                public void withAction(Action<File> action) {
                    this.action = action;
                }

                @Nested
                public Action<File> getAction() {
                    return action;
                }
            }
        """
    }

    private TestFile taskWithNestedBeanWithAction(TestFile projectDir = temporaryFolder.testDirectory) {
        nestedBeanWithAction(projectDir)
        return projectDir.file("buildSrc/src/main/java/TaskWithNestedBeanWithAction.java") << """
            import org.gradle.api.Action;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.NonNullApi;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.OutputFile;
            import org.gradle.api.tasks.TaskAction;

            import java.io.File;

            @NonNullApi
            public class TaskWithNestedBeanWithAction extends DefaultTask {
                private File outputFile = new File(getTemporaryDir(), "output.txt");
                private NestedBeanWithAction bean;

                @OutputFile
                public File getOutputFile() {
                    return outputFile;
                }

                public void setOutputFile(File outputFile) {
                    this.outputFile = outputFile;
                }

                @Nested
                public NestedBeanWithAction getBean() {
                    return bean;
                }

                public void setBean(NestedBeanWithAction bean) {
                    this.bean = bean;
                }

                @TaskAction
                public void doStuff() {
                    bean.getAction().execute(outputFile);
                }
            }
        """
    }

}
