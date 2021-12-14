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

import groovy.test.NotYetImplemented
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapability
import spock.lang.Issue

class DependencyManagementResultsAsInputsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        settingsFile << """
            includeBuild 'composite-lib'
            rootProject.name = 'root'
            include 'project-lib'
        """
        file('composite-lib/settings.gradle') << ""
        file('composite-lib/build.gradle') << """
            plugins { id 'java' }
            group = 'composite-lib'
        """
        mavenRepo.module("org.external", "external-lib").publish()
        file('lib/file-lib.jar') << 'content'
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
                compile 'composite-lib:composite-lib'
            }
        """
        // We need fresh daemons to exercise loading snapshots from disk in tests below
        executer.requireIsolatedDaemons()
    }

    def "can use #type as task input"() {
        given:
        buildFile << """
            import ${DefaultModuleIdentifier.name}
            import ${DefaultModuleVersionIdentifier.name}
            import ${DefaultModuleComponentIdentifier.name}
            import ${ImmutableCapability.name}
            import ${DefaultModuleComponentArtifactIdentifier.name}
            import ${ImmutableAttributesFactory.name}
            import ${DefaultResolvedVariantResult.name}
            import ${Describables.name}

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
        skipped(":verify")

        when:
        succeeds("verify", "-Dn=bar")

        then:
        executedAndNotSkipped(":verify")

        where:
        type                          | factory
        // For ResolvedArtifactResult
        "Attribute"                   | "Attribute.of(System.getProperty('n'), String)"
        "AttributeContainer"          | "services.get(ImmutableAttributesFactory).of(Attribute.of('some', String.class), System.getProperty('n'))"
        "Capability"                  | "new ImmutableCapability('group', System.getProperty('n'), '1.0')"
        "ModuleComponentIdentifier"   | "new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', System.getProperty('n')),'1.0')"
        "ComponentArtifactIdentifier" | "new DefaultModuleComponentArtifactIdentifier(new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', System.getProperty('n')),'1.0'), System.getProperty('n') + '-1.0.jar', 'jar', null)"
        "ResolvedVariantResult"       | "new DefaultResolvedVariantResult(new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', System.getProperty('n')), '1.0'), Describables.of('variantName'), services.get(ImmutableAttributesFactory).of(Attribute.of('some', String.class), System.getProperty('n')), [new ImmutableCapability('group', System.getProperty('n'), '1.0')], null)"
        // For ResolvedComponentResult
        "ModuleVersionIdentifier"     | "DefaultModuleVersionIdentifier.newId('group', System.getProperty('n'), '1.0')"
    }

    def "can use files from ResolvedArtifactResult as task input"() {
        given:
        buildFile << """
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

    def "can combine files and metadata from ResolvedArtifactResult as direct task inputs"() {
        given:
        buildFile << """
            abstract class TaskWithFilesAndMetadataInput extends DefaultTask {

                @InputFiles
                abstract ConfigurableFileCollection getFiles()

                @Input
                abstract ListProperty<ComponentArtifactIdentifier> getIds()

                @Input
                abstract ListProperty<Class<? extends Artifact>> getTypes()

                @Input
                abstract ListProperty<ResolvedVariantResult> getVariants()

                @OutputFile
                abstract RegularFileProperty getOutputFile()
            }

            tasks.register('verify', TaskWithFilesAndMetadataInput) {
                def resolvedArtifacts = configurations.compile.incoming.artifacts.resolvedArtifacts
                files.from(resolvedArtifacts.map { it.collect { it.file } })
                ids.set(resolvedArtifacts.map { it.collect { it.id } })
                types.set(resolvedArtifacts.map { it.collect { it.type } })
                variants.set(resolvedArtifacts.map { it.collect { it.variant } })
                outputFile.set(layout.buildDirectory.file('output.txt'))
                doLast {
                    println(files.files)
                    println(ids.get())
                    println(types.get())
                    println(variants.get())
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
        succeeds "verify", "-i"

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

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/13590")
    def "can combine files and metadata from ResolvedArtifactResult as nested task inputs"() {
        given:
        buildFile << """
            class ResolvedArtifactBean {

                @InputFile
                File file

                @Input
                ComponentArtifactIdentifier id

                @Input
                Class<? extends Artifact> type

                @Input
                ResolvedVariantResult variant
            }

            abstract class TaskWithFilesAndMetadataInput extends DefaultTask {

                @Nested
                abstract SetProperty<ResolvedArtifactBean>> getResArtifacts()

                @OutputFile
                abstract RegularFileProperty getOutputFile()
            }

            tasks.register('verify', TaskWithFilesAndMetadataInput) {
                def resolvedArtifacts = configurations.compile.incoming.artifacts.resolvedArtifacts
                resArtifacts.set(
                    resolvedArtifacts.map { arts ->
                        arts.collect { art ->
                            objects.newInstance(ResolvedArtifactBean).tap { bean ->
                                bean.file = art.file
                                bean.id = art.id
                                bean.type = art.type
                                bean.variant = art.variant
                            }
                        }
                    }
                )
                outputFile.set(layout.buildDirectory.file('output.txt'))
                doLast {
                    resArtifacts.get().each { art ->
                        println("\${art.file} - \${art.id} - \${art.type} - \${art.variant}")
                    }
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
        // In order to exercise loading previous execution snapshots from the on-disk cache
        new DaemonLogsAnalyzer(executer.daemonBaseDir).killAll()
    }
}
