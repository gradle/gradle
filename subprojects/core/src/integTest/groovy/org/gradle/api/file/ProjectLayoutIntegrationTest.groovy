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

    def "can attach a calculated directory to task property"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                final PropertyState<File> outputDir = project.providers.property(File)
                
                File getOutputDir() { return outputDir.getOrNull() }
                
                void setOutputDir(File f) { outputDir.set(f) }

                void setOutputDir(Provider<File> f) { outputDir.set(f) }
                
                @TaskAction
                void go() {
                    println "task output dir: " + outputDir.get() 
                }
            }
            
            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.ext.childDirName = "child"
                    def t = p.tasks.create("show", SomeTask)
                    t.outputDir = p.layout.buildDir.dir("some-dir").dir(p.providers.provider { p.childDirName })
                    println "plugin output dir: " + t.outputDir
                }
            }
            
            apply plugin: SomePlugin
            buildDir = "output"
            childDirName = "other-child"
"""

        when:
        run("show")

        then:
        outputContains("plugin output dir: " + testDirectory.file("build/some-dir/child"))
        outputContains("task output dir: " + testDirectory.file("output/some-dir/other-child"))
    }
}
