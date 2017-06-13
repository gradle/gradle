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
    def "can access the project dir and build dir"() {
        buildFile << """
            println "project dir: " + layout.projectDirectory.get()
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

    def "layout is available for injection"() {
        buildFile << """
            import javax.inject.Inject
            
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
        buildFile << """
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
        buildFile << """
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
}
