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
import org.gradle.initialization.StartParameterBuildOptions.BuildCacheDebugLoggingOption
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.ToBeImplemented
import spock.lang.Unroll

class NestedInputIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "nested #type.simpleName input adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }
            
            class NestedBeanWithInput {
                @Input${kind}
                ${type.name} input
            }
            
            class GeneratorTask extends DefaultTask {
                @Output${kind}
                ${type.name} output = newOutput${kind}()
                
                @TaskAction
                void doStuff() {
                    output${generatorAction}
                }
            }
            
            task generator(type: GeneratorTask) {
                output.set(project.layout.buildDirectory.${kind == 'Directory' ? 'dir' : 'file'}('output'))
            }
            
            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(input: newInput${kind}())
                bean.input.set(generator.output)
            }
        """

        when:
        run 'consumer'
        then:
        executedAndNotSkipped(':generator', ':consumer')

        where:
        kind        | type                | generatorAction
        'File'      | RegularFileProperty | '.getAsFile().get().text = "Hello"'
        'Directory' | DirectoryProperty   | '''.file('output.txt').get().getAsFile().text = "Hello"'''
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
                RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void doStuff() {
                    outputFile.getAsFile().get().text = "Hello"
                }
            }
            
            task generator(type: GeneratorTask) {
                outputFile.set(project.layout.buildDirectory.file('output'))
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

    @ToBeImplemented
    def "nested input using output file property of different task adds a task dependency"() {
        buildFile << """
            class TaskWithNestedProperty extends DefaultTask  {
                @Nested
                Object bean
            }
            
            class NestedBeanWithInput {
                @InputFile
                RegularFileProperty file
            }
            
            class GeneratorTask extends DefaultTask {
                @OutputFile
                RegularFileProperty outputFile = newOutputFile()
                
                @TaskAction
                void doStuff() {
                    outputFile.getAsFile().get().text = "Hello"
                }
            }
            
            task generator(type: GeneratorTask) {
                outputFile.set(project.layout.buildDirectory.file('output'))
            }
            
            task consumer(type: TaskWithNestedProperty) {
                bean = new NestedBeanWithInput(file: generator.outputFile)
            }
        """

        when:
        run 'consumer'
        then:
        // FIXME: Should have been executed
        notExecuted(':generator')
        // FIXME: Should have been executed
        skipped(':consumer')
    }

    @Unroll
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

    @Unroll
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

    @Unroll
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

    @Unroll
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
                RegularFileProperty outputFile = newOutputFile()
    
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
            
            def firstString = project.findProperty('firstInput')
            def firstBean = new NestedBean(firstInput: firstString, firstOutputFile: file("${firstOutputFile}"), firstInputFile: file("${firstInputFile}"))

            def secondString = project.findProperty('secondInput')
            def secondBean = new OtherNestedBean(secondInput: secondString, secondOutputFile: file("${secondOutputFile}"), secondInputFile: file("${secondInputFile}"))

            task taskWithNestedProperty(type: TaskWithNestedProperty) {
                bean = firstBean
                outputFile.set(project.layout.buildDirectory.file('output.txt'))
            }
        """
        }
    }

    def "execution fails when a nested property throws an exception"() {
        buildFile << """
            class TaskWithFailingNestedInput extends DefaultTask {
                @Nested
                Object getNested() {
                    throw new RuntimeException("BOOM")
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
    }

    def "null on nested bean is validated"() {
        buildFile << """
            class TaskWithAbsentNestedInput extends DefaultTask {
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
            
            task myTask(type: TaskWithAbsentNestedInput) {
                outputFile = file('build/output.txt')
            }            
        """

        expect:
        fails "myTask"
        failure.assertHasDescription("A problem was found with the configuration of task ':myTask'.")
        failure.assertHasCause("No value has been specified for property 'nested'.")
    }

    def "null on optional nested bean is allowed"() {
        buildFile << """
            class TaskWithAbsentNestedInput extends DefaultTask {
                @Nested
                @Optional
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
            
            task myTask(type: TaskWithAbsentNestedInput) {
                outputFile = file('build/output.txt')
            }            
        """

        expect:
        succeeds "myTask"
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
            
            boolean useOther = project.findProperty('useOther')
            
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

    def "elements of nested iterable cannot be null"() {
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
                beans = [new NestedBean(input: 'input'), null]
            }
        """

        expect:
        fails 'myTask'
        failure.assertHasCause('Null is not allowed as nested property \'beans.$1\'')
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
            
            def inputString = project.findProperty('input') ?: 'input'
            
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

    def "input changes for task with named nested beans"() {
        buildFile << taskWithNestedInput()
        buildFile << namedBeanClass()
        buildFile << """                                   
            myTask.nested = [new NamedBean(project.property('namedName'), 'value1'), new NamedBean('name', 'value2')]           
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
        output.contains("Input property 'nested.different\$0.class' has been added for task ':myTask'")
        output.contains("Input property 'nested.name1\$0.class' has been removed for task ':myTask'")
    }

    def "input changes for task with nested map"() {
        buildFile << taskWithNestedInput()
        buildFile << nestedBeanWithStringInput()
        buildFile << """                                   
            myTask.nested = [(project.property('key')): new NestedBean('value1'), key2: new NestedBean('value2')]           
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
        output.contains("Input property 'nested.different.class' has been added for task ':myTask'")
        output.contains("Input property 'nested.key1.class' has been removed for task ':myTask'")
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

    def "implementation of nested property in Groovy build script is tracked"() {
        setupTaskClassWithNestedAction()
        buildFile << """
            task myTask(type: TaskWithNestedAction) {
                action = ${originalImplementation}
            }
        """

        buildFile.makeOlder()

        when:
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        buildFile.text = """
            task myTask(type: TaskWithNestedAction) {
                action = ${changedImplementation}
            }
        """
        run 'myTask', '--info'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
        output.contains "Value of input property 'action.class' has changed for task ':myTask'"

        where:
        originalImplementation                  | changedImplementation
        '{ it.text = "hello" }'                 | '{ it.text = "changed" }'
        wrapAction('outputFile.text = "hello"') | wrapAction('outputFile.text = "changed"')
    }

    private static String wrapAction(String body) {
        """
            new Action() {
                void execute(outputFile) {
                    ${body}
                }
            }
        """
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "implementations in nested property defined by Java 8 lambda is tracked"() {
        setupTaskClassWithNestedAction()
        file('buildSrc/src/main/java/LambdaActions.java') << """
            import org.gradle.api.Action;
            
            import java.io.File;
            import java.io.IOException;
            import java.nio.file.Files;
            
            public class LambdaActions {
                public static final Action<File> ORIGINAL = file -> {
                    try {
                        Files.write(file.toPath(), "original".getBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
                
                public static final Action<File> CHANGED = file -> {
                    try {
                        Files.write(file.toPath(), "changed".getBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
            }
        """
        buildFile << """
            task myTask(type: TaskWithNestedAction) {
                action = LambdaActions.ORIGINAL
            }
        """

        buildFile.makeOlder()

        when:
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        buildFile.text = """
            task myTask(type: TaskWithNestedAction) {
                action = LambdaActions.CHANGED
            }
        """
        run 'myTask', '--info'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
        output.contains "Value of input property 'action.class' has changed for task ':myTask'"
    }

    private TestFile setupTaskClassWithNestedAction() {
        file("buildSrc/src/main/java/TaskWithNestedAction.java") << """
            import org.gradle.api.Action;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.NonNullApi;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.OutputFile;
            import org.gradle.api.tasks.TaskAction;
            
            import java.io.File;
            
            @NonNullApi
            public class TaskWithNestedAction extends DefaultTask {
                private File outputFile = new File(getTemporaryDir(), "output.txt");
                private Action<File> action;
            
                @OutputFile
                public File getOutputFile() {
                    return outputFile;
                }
            
                public void setOutputFile(File outputFile) {
                    this.outputFile = outputFile;
                }
            
                @Nested
                public Action<File> getAction() {
                    return action;
                }
            
                public void setAction(Action<File> action) {
                    this.action = action;
                }
            
                @TaskAction
                public void doStuff() {
                    getAction().execute(outputFile);
                }
            }
        """
    }

    def "task with nested bean loaded with custom classloader is not cached"() {
        file("input.txt").text = "data"
        buildFile << taskWithNestedBeanFromCustomClassLoader()

        when:
        withBuildCache().run "customTask", "--info", "-D${BuildCacheDebugLoggingOption.GRADLE_PROPERTY}=true"
        then:
        output.contains "The implementation of 'bean' cannot be determined, because it was loaded by an unknown classloader"
        output.contains "Not caching task ':customTask' because no valid cache key was generated"
    }

    def "task with nested bean loaded with custom classloader is never up-to-date"() {
        file("input.txt").text = "data"
        buildFile << taskWithNestedBeanFromCustomClassLoader()

        when:
        run "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        run "customTask", "--info"
        then:
        executedAndNotSkipped ":customTask"
        output.contains "Value of input property 'bean.class' has changed for task ':customTask'"
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
                    @InputFile File input
                    @OutputFile File output
                }
            '''

            task customTask(type: TaskWithNestedProperty) {
                bean = NestedBean.newInstance()
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
        output.contains "Value of input property 'bean.action.class' has changed for task ':myTask'"
    }

    private TestFile nestedBeanWithAction() {
        return file("buildSrc/src/main/java/NestedBeanWithAction.java") << """
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.Action;
            import java.io.File;
            
            public class NestedBeanWithAction {
                Action<File> action;
                
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
    private TestFile taskWithNestedBeanWithAction() {
        nestedBeanWithAction()
        return file("buildSrc/src/main/java/TaskWithNestedBeanWithAction.java") << """
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
