/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer

class DependencyManagementResultsAsInputsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'project-lib'
        """
        mavenRepo.module("org.external", "external-lib").publish()
        file('lib/file-lib.jar') << 'content'
        // We need fresh daemons to load snapshots from disk in tests below
        executer.requireIsolatedDaemons()
    }

    def "can use #type as task input"() {
        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
            import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
            import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
            import org.gradle.internal.component.external.model.ImmutableCapability
            import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier

            abstract class TaskWithInput extends DefaultTask {

                @Input
                abstract Property<$type> getInput()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                def action() {
                    println(input.get())
                }
            }

            tasks.register("verify", TaskWithInput) {
                outputFile.set(layout.buildDirectory.file('output.txt'))
                input.set($factory)
            }
        """

        when:
        succeeds("verify", "-Dn=foo")

        then:
        executedAndNotSkipped(":verify")

        when:
        killDaemons()
        succeeds("verify", "-Dn=foo")

        then:

        then:
        skipped(":verify")

        when:
        succeeds("verify", "-Dn=bar")

        then:
        executedAndNotSkipped(":verify")

        where:
        type                          | factory
        // For ResolvedArtifactResult
        "Attribute"                   | "Attribute.of(System.getProperty('n'), String)"
        "Capability"                  | "new ImmutableCapability('group', System.getProperty('n'), '1.0')"
        "ModuleComponentIdentifier"   | "new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', System.getProperty('n')),'1.0')"
        "ComponentArtifactIdentifier" | "new DefaultModuleComponentArtifactIdentifier(new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', System.getProperty('n')),'1.0'), System.getProperty('n') + '-1.0.jar', 'jar', null)"
        "ModuleVersionIdentifier"     | "DefaultModuleVersionIdentifier.newId('group', System.getProperty('n'), '1.0')"
    }

    def "can use files from ResolvedArtifactResult as task input"() {
        given:
        buildFile << """
            project(':project-lib') {
                apply plugin: 'java'
            }
            configurations {
                compile
            }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'org.external:external-lib:1.0'
                compile project('project-lib')
                compile files('lib/file-lib.jar')
            }

            abstract class TaskWithFilesInput extends DefaultTask {

                @InputFiles
                abstract ConfigurableFileCollection getInputFiles()

                @OutputFile
                abstract RegularFileProperty getOutputFile()
            }

            tasks.register('verify', TaskWithFilesInput) {
                inputFiles.from(configurations.compile.incoming.artifacts.resolvedArtifacts.map { it.collect { it.file } })
                outputFile.set(layout.buildDirectory.file('output.txt'))
                doLast {
                    println(inputFiles.files)
                }
            }
        """

        def sourceFile = file("project-lib/src/main/java/Main.java")
        sourceFile << """
            class Main {}
        """.stripIndent()
        sourceFile.makeOlder()

        when:
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"

        when:
        killDaemons()
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":verify"

        when:
        sourceFile.text = """
            class Main {
                public static void main(String[] args) {}
            }
        """.stripIndent()
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"
    }

    def "can combine files and metadata from ResolvedArtifactResult as task input"() {
        given:
        buildFile << """
            project(':project-lib') {
                apply plugin: 'java'
            }
            configurations {
                compile
            }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'org.external:external-lib:1.0'
                compile project('project-lib')
                compile files('lib/file-lib.jar')
            }

            interface FilesAndMetadata {

                @InputFiles
                ConfigurableFileCollection getInputFiles()

                @Input
                SetProperty<Attribute<?>> getMetadata()
            }

            abstract class TaskWithFilesAndMetadataInput extends DefaultTask {

                private final FilesAndMetadata filesAndMetadata = project.objects.newInstance(FilesAndMetadata)

                @Nested
                FilesAndMetadata getFilesAndMetadata() {
                    return filesAndMetadata
                }

                @OutputFile
                abstract RegularFileProperty getOutputFile()
            }

            tasks.register('verify', TaskWithFilesAndMetadataInput) {
                def resolvedArtifacts = configurations.compile.incoming.artifacts.resolvedArtifacts
                filesAndMetadata.inputFiles.from(resolvedArtifacts.map { it.collect { it.file } })
                filesAndMetadata.metadata.addAll(resolvedArtifacts.map { it.variant.collectMany { it.attributes.keySet() } })
                outputFile.set(layout.buildDirectory.file('output.txt'))
                doLast {
                    println(filesAndMetadata.inputFiles.files)
                    println(filesAndMetadata.metadata.get())
                }
            }
        """

        def sourceFile = file("project-lib/src/main/java/Main.java")
        sourceFile << """
            class Main {}
        """.stripIndent()
        sourceFile.makeOlder()

        when:
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"

        when:
        killDaemons()
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":verify"

        when:
        sourceFile.text = """
            class Main {
                public static void main(String[] args) {}
            }
        """.stripIndent()
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"
    }

    private void killDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir).killAll()
    }
}
