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

package org.gradle.api.file


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ProjectLayoutIntegrationTest extends AbstractIntegrationSpec {
    private static final String STRING_CALLABLE = 'new java.util.concurrent.Callable<String>() { String call() { return "src/resource/file.txt" } }'

    def "can access the project dir and build dir"() {
        buildFile """
            println "project dir: " + layout.projectDirectory.asFile
            def b = layout.buildDirectory
            println "build dir: " + b.get()
            buildDir = "output"
            println "build dir 2: " + b.get()
"""

        when:
        run()

        then:
        outputContains("project dir: " + testDirectory)
        outputContains("build dir: " + testDirectory.file("build"))
        outputContains("build dir 2: " + testDirectory.file("output"))
    }

    def "can apply convention to build dir"() {
        buildFile """
            println "build dir: " + project.buildDir
            layout.buildDirectory.convention(layout.projectDirectory.dir("out"))
            println "build dir 2: " + project.buildDir
            layout.buildDirectory = layout.projectDirectory.dir("target")
            println "build dir 3: " + project.buildDir
            layout.buildDirectory.convention(layout.projectDirectory.dir("out"))
            println "build dir 4: " + project.buildDir
"""

        when:
        run()

        then:
        outputContains("build dir: " + testDirectory.file("build"))
        outputContains("build dir 2: " + testDirectory.file("out"))
        outputContains("build dir 3: " + testDirectory.file("target"))
        outputContains("build dir 4: " + testDirectory.file("target"))
    }

    def "layout is available for injection"() {
        buildFile """
            class SomeTask extends DefaultTask {
                @Inject
                ProjectLayout getLayout() { null }

                @TaskAction
                void go() {
                    println "task build dir: " + layout.buildDirectory.get()
                }
            }

            class SomePlugin implements Plugin<Project> {
                @Inject SomePlugin(ProjectLayout layout) {
                    println "plugin build dir: " + layout.buildDirectory.get()
                }

                void apply(Project p) {
                    p.tasks.create("show", SomeTask)
                }
            }

            apply plugin: SomePlugin
            buildDir = "output"
"""

        when:
        run("show")

        then:
        outputContains("plugin build dir: " + testDirectory.file("build"))
        outputContains("task build dir: " + testDirectory.file("output"))
    }

    def "can define and resolve calculated directory relative to project and build directory"() {
        buildFile """
            def childDirName = "child"
            def srcDir = layout.projectDir.dir("src").dir(providers.provider { childDirName })
            def outputDir = layout.buildDirectory.dir(providers.provider { childDirName })
            println "src dir 1: " + srcDir.get()
            println "output dir 1: " + outputDir.get()
            buildDir = "output/some-dir"
            childDirName = "other-child"
            println "src dir 2: " + srcDir.get()
            println "output dir 2: " + outputDir.get()
"""

        when:
        run()

        then:
        outputContains("src dir 1: " + testDirectory.file("src/child"))
        outputContains("output dir 1: " + testDirectory.file("build/child"))
        outputContains("src dir 2: " + testDirectory.file("src/other-child"))
        outputContains("output dir 2: " + testDirectory.file("output/some-dir/other-child"))
    }

    def "can define and resolve calculated file relative to project and build directory"() {
        buildFile """
            def childDirName = "child"
            def srcFile = layout.projectDir.dir("src").file(providers.provider { childDirName })
            def outputFile = layout.buildDirectory.file(providers.provider { childDirName })
            println "src file 1: " + srcFile.get()
            println "output file 1: " + outputFile.get()
            buildDir = "output/some-dir"
            childDirName = "other-child"
            println "src file 2: " + srcFile.get()
            println "output file 2: " + outputFile.get()
"""

        when:
        run()

        then:
        outputContains("src file 1: " + testDirectory.file("src/child"))
        outputContains("output file 1: " + testDirectory.file("build/child"))
        outputContains("src file 2: " + testDirectory.file("src/other-child"))
        outputContains("output file 2: " + testDirectory.file("output/some-dir/other-child"))
    }

    def "can use file() method to resolve locations created relative to the project dir and build dir"() {
        buildFile << """
            def location = $expression
            println "location: " + file(location)
"""

        when:
        run()

        then:
        outputContains("location: " + testDirectory.file(resolvesTo))

        where:
        expression                                                             | resolvesTo
        "layout.projectDirectory.dir('src/main/java')"                         | "src/main/java"
        "layout.projectDirectory.dir(providers.provider { 'src/main/java' })"  | "src/main/java"
        "layout.projectDirectory.file('src/main/java')"                        | "src/main/java"
        "layout.projectDirectory.file(providers.provider { 'src/main/java' })" | "src/main/java"
        "layout.buildDirectory.dir('classes/main')"                            | "build/classes/main"
        "layout.buildDirectory.dir(providers.provider { 'classes/main' })"     | "build/classes/main"
        "layout.buildDirectory.file('classes/main')"                           | "build/classes/main"
        "layout.buildDirectory.file(providers.provider { 'classes/main' })"    | "build/classes/main"
    }

    def "can construct file collection containing locations created relative to the project dir and build dir"() {
        buildFile << """
            def l = $expression
            def c = files(l)
            println "files 1: " + c.files
            buildDir = 'output'
            println "files 2: " + c.files
"""

        when:
        run()

        then:
        outputContains("files 1: [" + testDirectory.file(resolvesTo1) + "]")
        outputContains("files 2: [" + testDirectory.file(resolvesTo2) + "]")

        where:
        expression                                   | resolvesTo1          | resolvesTo2
        "layout.buildDirectory.dir('classes/main')"  | "build/classes/main" | "output/classes/main"
        "layout.buildDirectory.file('exe/main.exe')" | "build/exe/main.exe" | "output/exe/main.exe"
    }

    def 'can create empty #collectionType'() {
        given:
        buildFile << """
            def fileCollection = $expression
            println("size = \${fileCollection.files.size()}")
        """

        when:
        run()

        then:
        outputContains('size = 0')

        where:
        collectionType               | expression
        'FileCollection'             | 'project.layout.files()'
    }

    def 'can create #collectionType containing #content'() {
        given:
        file('src/resource/file.txt') << "some text"

        buildFile << """
            def fileCollection = $expression
            println("size = \${fileCollection.files.size()}")
        """

        when:
        run()

        then:
        outputContains('size = 1')

        where:
        collectionType               | content          | expression
        'FileCollection'             | 'String'         | 'project.layout.files("src/resource/file.txt")'
        'FileCollection'             | 'File'           | 'project.layout.files(new File("src/resource/file.txt"))'
        'FileCollection'             | 'Path'           | 'project.layout.files(java.nio.file.Paths.get("src/resource/file.txt"))'
        'FileCollection'             | 'URI'            | 'project.layout.files(new File(projectDir, "/src/resource/file.txt").toURI())'
        'FileCollection'             | 'URL'            | 'project.layout.files(new File(projectDir, "/src/resource/file.txt").toURI().toURL())'
        'FileCollection'             | 'Directory'      | 'project.layout.files(project.layout.projectDirectory)'
        'FileCollection'             | 'RegularFile'    | 'project.layout.files(project.layout.projectDirectory.file("src/resource/file.txt"))'
        'FileCollection'             | 'Closure'        | 'project.layout.files({ "src/resource/file.txt" })'
        'FileCollection'             | 'List'           | 'project.layout.files([ "src/resource/file.txt" ])'
        'FileCollection'             | 'array'          | 'project.layout.files([ "src/resource/file.txt" ] as Object[])'
        'FileCollection'             | 'FileCollection' | "project.layout.files(project.layout.files('src/resource/file.txt'))"
        'FileCollection'             | 'Callable'       | "project.layout.files($STRING_CALLABLE)"
        'FileCollection'             | 'Provider'       | "project.layout.files(provider($STRING_CALLABLE))"
        'FileCollection'             | 'nested objects' | "project.layout.files({[{$STRING_CALLABLE}]})"
    }

    def 'can create #collectionType with #dependencyType dependency'() {
        buildFile << """
            task myTask {
                def outputFile = file('build/resource/file.txt')
                doLast {
                    outputFile.text = "some text"
                }
                outputs.file outputFile
            }

            def fileCollection = $expression
            println("files = \${fileCollection.files}")
        """

        when:
        run('myTask')

        then:
        outputContains("files = [${testDirectory.file('/build/resource/file.txt').absolutePath}]")

        where:
        collectionType               | dependencyType | expression
        'FileCollection'             | 'Task'         | 'project.layout.files(project.tasks.myTask)'
        'FileCollection'             | 'TaskOutputs'  | 'project.layout.files(project.tasks.myTask.outputs)'
    }

    def '#expression enforces build dependencies when given Task as input'() {
        buildFile << """
            task producer {
                def outputFile = file('build/resource/file.txt')
                outputs.file outputFile
                doLast {
                    outputFile.text = "some text"
                }
            }

            task consumer {
                def fileCollection = $expression(project.tasks.producer)
                inputs.files fileCollection
                doLast {
                    println("files = \${fileCollection.files}")
                }
            }
        """

        when:
        run('consumer')

        then:
        executed(':producer', ':consumer')
        outputContains("files = [${testDirectory.file('/build/resource/file.txt').absolutePath}]")

        where:
        expression << ['project.layout.files']
    }

    def 'can create #collectionType with Configuration dependency'() {
        file('src/resource/file.txt') << "some text"
        buildFile << """
            configurations {
                other
            }

            dependencies {
                other files("src/resource/file.txt")
            }

            def fileCollection = $expression
            println("files = \${fileCollection.files}")
        """

        when:
        run()

        then:
        outputContains("files = [${testDirectory.file('/src/resource/file.txt').absolutePath}]")

        where:
        collectionType               | expression
        'FileCollection'             | 'project.layout.files(configurations.other)'
    }

    def 'fails to resolve #collectionType with null element'() {
        buildFile << """
            def fileCollection = $expression
            println("size = \${fileCollection.files.size()}")
        """

        expect:
        executer.withStacktraceEnabled()
        fails('help')
        errorOutput.contains('java.lang.NullPointerException')

        where:
        collectionType               | expression
        'FileCollection (Object...)' | 'project.layout.files((Object) null)'
        'FileCollection (File...)'   | 'project.layout.files((File) null)'
    }
}
