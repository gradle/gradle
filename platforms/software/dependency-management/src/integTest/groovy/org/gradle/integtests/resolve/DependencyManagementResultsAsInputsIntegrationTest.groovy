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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DefaultComponentSelectionDescriptor
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class DependencyManagementResultsAsInputsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        server.start()

        settingsFile << """
            includeBuild 'composite-lib'
            rootProject.name = 'root'
            include 'project-lib'
        """
        def variantDeclaration = { sysPropName ->
            """
                def myAttribute = Attribute.of("my.attribute.name", String)
                dependencies.attributesSchema { attribute(myAttribute) }
                configurations {
                    runtimeElements {
                        attributes { attribute(myAttribute, System.getProperty('$sysPropName', 'default-value')) }
                    }
                }
            """
        }
        def unselectedVariantDeclaration = { sysPropName ->
            """
                def myUnselectedAttribute = Attribute.of("my.unselected.attribute.name", String)
                dependencies.attributesSchema { attribute(myUnselectedAttribute) }
                configurations {
                    mainSourceElements {
                        attributes { attribute(myUnselectedAttribute, System.getProperty('$sysPropName', 'default-value')) }
                    }
                }
            """
        }
        file('composite-lib/settings.gradle') << ""
        file('composite-lib/build.gradle') << """
            plugins { id 'java-library' }
            group = 'composite-lib'
            ${variantDeclaration('compositeLibAttrValue')}
        """
        def util = mavenHttpRepo.module("org.external", "external-util").publish().allowAll()
        mavenHttpRepo.module("org.external", "external-lib")
            .dependsOn(util)
            .publish()
            .allowAll()
        mavenHttpRepo.module("org.external", "external-lib2")
            .dependsOn(util)
            .withModuleMetadata()
            .publish()
            .allowAll()
        mavenHttpRepo.module("org.external", "external-tool").publish().allowAll()
        file('lib/file-lib.jar') << 'content'
        buildFile << """
            project(':project-lib') {
                apply plugin: 'java-library'
                ${variantDeclaration('projectLibAttrValue')}
                ${unselectedVariantDeclaration('projectUnselectedLibAttrValue')}
            }
            apply plugin: 'java-library'
            repositories { maven { url = "${mavenHttpRepo.uri}" } }
            dependencies {
                implementation('org.external:external-lib:1.0') {
                    because(System.getProperty('selectionReason', 'original'))
                }
                implementation 'org.external:external-lib2:1.0'
                implementation project('project-lib')
                implementation files('lib/file-lib.jar')
                implementation 'composite-lib:composite-lib'
            }

            @CacheableRule
            abstract class ChangingAttributeRule implements ComponentMetadataRule {
                final String attrValue
                @Inject ChangingAttributeRule(String attrValue) { this.attrValue = attrValue }
                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        attributes.attribute(Attribute.of("my.attribute.name", String), attrValue)
                    }
                }
            }

            dependencies {
                components {
                    withModule('org.external:external-lib', ChangingAttributeRule) {
                        params(System.getProperty('externalLibAttrValue', 'default-value'))
                    }
                }
            }
        """
        withOriginalSourceIn("project-lib")
        withOriginalSourceIn("composite-lib")
    }

    def "can not use ResolvedArtifactResult as task input annotated with #annotation"() {

        executer.beforeExecute {
            executer.noDeprecationChecks() // Cannot convert the provided notation to a File or URI
            executer.withArgument("-Dorg.gradle.internal.max.validation.errors=20")
        }

        given:
        buildFile << """
            interface NestedBean {
                $annotation
                Property<ResolvedArtifactResult> getNested()
            }

            abstract class TaskWithInput extends DefaultTask {

                private final NestedBean nested = project.objects.newInstance(NestedBean.class)

                $annotation
                ResolvedArtifactResult getDirect() { null }

                $annotation
                Provider<ResolvedArtifactResult> getProviderInput() { propertyInput }

                $annotation
                abstract Property<ResolvedArtifactResult> getPropertyInput();

                $annotation
                abstract SetProperty<ResolvedArtifactResult> getSetPropertyInput();

                $annotation
                abstract ListProperty<ResolvedArtifactResult> getListPropertyInput();

                $annotation
                abstract MapProperty<String, ResolvedArtifactResult> getMapPropertyInput();

                @Nested
                abstract NestedBean getNestedInput();
            }

            tasks.register('verify', TaskWithInput) {
                def artifacts = configurations.runtimeClasspath.incoming.artifacts
                propertyInput.set(artifacts.resolvedArtifacts.map { it[0] })
                setPropertyInput.set(artifacts.resolvedArtifacts)
                listPropertyInput.set(artifacts.resolvedArtifacts)
                mapPropertyInput.put("some", artifacts.resolvedArtifacts.map { it[0] })
                nestedInput.nested.set(artifacts.resolvedArtifacts.map { it[0] })
                doLast {
                    println(setPropertyInput.get())
                }
            }
        """

        when:
        fails "verify"

        then:
        failureDescriptionStartsWith("Some problems were found with the configuration of task ':verify' (type 'TaskWithInput').")
        failureDescriptionContains("Type 'TaskWithInput' property 'direct' has $annotation annotation used on property of type 'ResolvedArtifactResult'.")
        failureDescriptionContains("Type 'TaskWithInput' property 'providerInput' has $annotation annotation used on property of type 'Provider<ResolvedArtifactResult>'.")
        failureDescriptionContains("Type 'TaskWithInput' property 'propertyInput' has $annotation annotation used on property of type 'Property<ResolvedArtifactResult>'.")
        failureDescriptionContains("Type 'TaskWithInput' property 'setPropertyInput' has $annotation annotation used on property of type 'SetProperty<ResolvedArtifactResult>'.")
        failureDescriptionContains("Type 'TaskWithInput' property 'listPropertyInput' has $annotation annotation used on property of type 'ListProperty<ResolvedArtifactResult>'.")
        failureDescriptionContains("Type 'TaskWithInput' property 'mapPropertyInput' has $annotation annotation used on property of type 'MapProperty<String, ResolvedArtifactResult>'.")
        failureDescriptionContains("Type 'TaskWithInput' property 'nestedInput.nested' has $annotation annotation used on property of type 'Property<ResolvedArtifactResult>'.")

        // Because
        failureDescriptionContains("ResolvedArtifactResult is not supported on task properties annotated with $annotation.")

        // Possible solutions
        failureDescriptionContains("1. Extract artifact metadata and annotate with @Input.")
        failureDescriptionContains("2. Extract artifact files and annotate with @InputFiles.")

        // Documentation
        failureDescriptionContains(documentationRegistry.getDocumentationRecommendationFor("information", "validation_problems", "unsupported_value_type"))

        where:
        annotation    | _
        "@Input"      | _
        "@InputFile"  | _
        "@InputFiles" | _
    }

    def "can use #type as task input"() {
        given:
        buildFile << """
            import ${DefaultModuleIdentifier.name}
            import ${DefaultModuleVersionIdentifier.name}
            import ${DefaultModuleComponentIdentifier.name}
            import ${DefaultImmutableCapability.name}
            import ${DefaultModuleComponentArtifactIdentifier.name}
            import ${AttributesFactory.name}
            import ${DefaultResolvedVariantResult.name}
            import ${ImmutableCapabilities.name}
            import ${Describables.name}
            import ${DefaultComponentSelectionDescriptor.name}
            import ${ComponentSelectionReasons.name}
            import ${DefaultLibraryComponentSelector.name}

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
        succeeds("verify", "-Dn=foo")

        then:
        skipped(":verify")

        when:
        succeeds("verify", "-Dn=bar")

        then:
        executedAndNotSkipped(":verify")

        where:
        type                           | factory
        // For ResolvedArtifactResult
        "Attribute"                    | "Attribute.of(System.getProperty('n'), String)"
        "AttributeContainer"           | "services.get(AttributesFactory).of(Attribute.of('some', String.class), System.getProperty('n'))"
        "Capability"                   | "new DefaultImmutableCapability('group', System.getProperty('n'), '1.0')"
        "ModuleComponentIdentifier"    | "new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', System.getProperty('n')),'1.0')"
        "ComponentArtifactIdentifier"  | "new DefaultModuleComponentArtifactIdentifier(new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', System.getProperty('n')),'1.0'), System.getProperty('n') + '-1.0.jar', 'jar', null)"
        "ResolvedVariantResult"        | "new DefaultResolvedVariantResult(new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', System.getProperty('n')), '1.0'), Describables.of('variantName'), services.get(AttributesFactory).of(Attribute.of('some', String.class), System.getProperty('n')), ImmutableCapabilities.of(new DefaultImmutableCapability('group', System.getProperty('n'), '1.0')), null)"
        // For ResolvedComponentResult
        "ModuleVersionIdentifier"      | "DefaultModuleVersionIdentifier.newId('group', System.getProperty('n'), '1.0')"
//        "ResolvedComponentResult"      | "null"
//        "DependencyResult"             | "null"
        "ComponentSelector"            | "new DefaultLibraryComponentSelector(':sub', System.getProperty('n'))"
        "ComponentSelectionReason"     | "ComponentSelectionReasons.of(ComponentSelectionReasons.REQUESTED.withDescription(Describables.of('csd-' + System.getProperty('n'))))"
        "ComponentSelectionDescriptor" | "new DefaultComponentSelectionDescriptor(ComponentSelectionCause.REQUESTED, Describables.of('csd-' + System.getProperty('n')))"
    }

    def "can map ResolvedArtifactResult file as task input"() {
        given:
        buildFile << """
            abstract class TaskWithFilesInput extends DefaultTask {

                @InputFiles
                abstract ConfigurableFileCollection getInputFiles()

                @OutputFile
                abstract RegularFileProperty getOutputFile()
            }

            tasks.register('verify', TaskWithFilesInput) {
                inputFiles.from(configurations.runtimeClasspath.incoming.artifacts.resolvedArtifacts.map { it.collect { it.file } })
                outputFile.set(layout.buildDirectory.file('output.txt'))
                doLast {
                    println(inputFiles.files)
                }
            }
        """

        when:
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"

        when:
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":verify"

        when:
        withChangedSourceIn("project-lib")
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"

        when:
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":verify"

        when:
        withChangedSourceIn("composite-lib")
        succeeds "verify"

        then:
        executedAndNotSkipped ":composite-lib:jar", ":verify"

        when:
        withNewExternalDependency()
        succeeds ":verify"

        then:
        skipped ":project-lib:jar", ":composite-lib:jar"
        executedAndNotSkipped ":verify"
    }

    def "can map ResolvedArtifactResult #inputProperty as task input"() {
        given:
        buildFile << """
            abstract class TaskWithResultInput extends DefaultTask {

                @Input
                abstract ListProperty<${inputType}> getInput()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction void action() {
                    println(input.get())
                }
            }

            tasks.register("verify", TaskWithResultInput) {
                def resolvedArtifacts = configurations.runtimeClasspath.incoming.artifacts.resolvedArtifacts
                input.set(resolvedArtifacts.map { it.collect { it.${inputProperty} } })
                outputFile.set(layout.buildDirectory.file('output.txt'))
            }
        """

        when:
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"

        when:
        succeeds "verify", "-i"

        then:
        skipped ":project-lib:jar", ":verify"

        when: "changing project library source code"
        withChangedSourceIn("project-lib")
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar"
        skipped ":verify"

        when:
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":verify"

        when: "changing composite library source code"
        withChangedSourceIn("composite-lib")
        succeeds "verify"

        then:
        executedAndNotSkipped ":composite-lib:jar"
        skipped ":verify"

        when: "adding a new external dependency"
        withNewExternalDependency()
        succeeds ":verify"

        then:
        skipped ":project-lib:jar", ":composite-lib:jar"
        executedAndNotSkipped ":verify"

        when: "changing project library variant metadata"
        succeeds "verify", "-DprojectLibAttrValue=new-value"

        then:
        if (inputProperty == "variant") {
            skipped ":project-lib:jar", ":composite-lib:jar"
            executedAndNotSkipped ":verify"
        } else {
            skipped ":project-lib:jar", ":composite-lib:jar", ":verify"
        }

        when: "changing included library variant metadata"
        succeeds "verify", "-DcompositeLibAttrValue=new-value"

        then:
        if (inputProperty == "variant") {
            skipped ":project-lib:jar", ":composite-lib:jar"
            executedAndNotSkipped ":verify"
        } else {
            skipped ":project-lib:jar", ":composite-lib:jar", ":verify"
        }

        when: "changing external library variant metadata"
        succeeds "verify", "-DexternalLibAttrValue=new-value"

        then:
        if (inputProperty == "variant") {
            skipped ":project-lib:jar", ":composite-lib:jar"
            executedAndNotSkipped ":verify"
        } else {
            skipped ":project-lib:jar", ":composite-lib:jar", ":verify"
        }

        where:
        inputProperty | inputType
        "id"          | "ComponentArtifactIdentifier"
        "type"        | "Class<? extends Artifact>"
        "variant"     | "ResolvedVariantResult"
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
                def resolvedArtifacts = configurations.runtimeClasspath.incoming.artifacts.resolvedArtifacts
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

        when:
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"

        when:
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":verify"

        when:
        withChangedSourceIn("project-lib")
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"

        when:
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":verify"

        when:
        withChangedSourceIn("composite-lib")
        succeeds "verify"

        then:
        executedAndNotSkipped ":composite-lib:jar", ":verify"

        when:
        withNewExternalDependency()
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":composite-lib:jar"
        executedAndNotSkipped ":verify"
    }

    private def resolvedComponentResultSetup() {
        buildFile << """
            abstract class TaskWithGraphInput extends DefaultTask {

                @Input
                abstract Property<ResolvedComponentResult> getDepGraphRoot()

                @OutputFile
                abstract RegularFileProperty getOutputFile()
            }

            tasks.register('verify', TaskWithGraphInput) {
                depGraphRoot.set(configurations.runtimeClasspath.incoming.resolutionResult.rootComponent)
                outputFile.set(layout.buildDirectory.file('output.txt'))
                doLast {
                    println(depGraphRoot.get())
                }
            }
        """
    }

    def "can use ResolvedComponentResult result as task input and changing source in '#changeLoc' doesn't invalidate the cache"() {
        given:
        resolvedComponentResultSetup()

        when: "Task without changes is executed & not skipped"
        succeeds "verify"

        then:
        executedAndNotSkipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "Running it again with the same environment skips the task"
        succeeds "verify"

        then:
        skipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "The change doesn't invalidate the cache"
        withChangedSourceIn(changeLoc)
        succeeds "verify"

        then:
        skipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        where:
        changeLoc << ['project-lib', 'composite-lib']
    }

    def "can use ResolvedComponentResult result as task input and '#changeDesc' invalidates the cache"() {
        given:
        resolvedComponentResultSetup()
        buildFile << """
            if (Boolean.getBoolean("externalDependency")) {
                dependencies { implementation 'org.external:external-tool:1.0' }
            }
        """

        when: "Task without changes is executed & not skipped"
        succeeds "verify"

        then:
        executedAndNotSkipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "Running it again with the same environment skips the task"
        succeeds "verify"

        then:
        skipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "Making a change invalidates the cache"
        succeeds "verify", changeArg

        then:
        executedAndNotSkipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "Keeping the change skips the task again"
        succeeds "verify", changeArg

        then:
        skipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "Losing the change invalidates the cache"
        succeeds "verify"

        then:
        executedAndNotSkipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        where:
        changeDesc                                   | changeArg
        "a new external dependency"                  | "-DexternalDependency=true"
        "changing selection reasons"                 | "-DselectionReason=changed"
        "changing project library variant metadata"  | "-DprojectLibAttrValue=new-value"
        "changing included library variant metadata" | "-DcompositeLibAttrValue=new-value"
        "changing external library variant metadata" | "-DexternalLibAttrValue=new-value"
    }

    def "can use ResolvedComponentResult result as task input and '#changeDesc' invalidates the cache (returnAllVariants=true)"() {
        given:
        resolvedComponentResultSetup()
        buildFile << """
            if (Boolean.getBoolean("externalDependency")) {
                dependencies { implementation 'org.external:external-tool:1.0' }
            }
            configurations.runtimeClasspath.resolutionStrategy.includeAllSelectableVariantResults = true
        """

        when: "Task without changes is executed & not skipped"
        succeeds "verify"

        then:
        executedAndNotSkipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "Running it again with the same environment skips the task"
        succeeds "verify"

        then:
        skipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "The change invalidates the cache"
        succeeds "verify", changeArg

        then:
        executedAndNotSkipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "Keeping the change skips the task again"
        succeeds "verify", changeArg

        then:
        skipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        when: "Losing the change invalidates the cache"
        succeeds "verify"

        then:
        executedAndNotSkipped ":verify"
        notExecuted ":project-lib:jar", ":composite-lib:jar"

        where:
        changeDesc                                             | changeArg
        "a new external dependency"                            | "-DexternalDependency=true"
        "changing selection reasons"                           | "-DselectionReason=changed"
        "changing project library variant metadata"            | "-DprojectLibAttrValue=new-value"
        "changing unselected project library variant metadata" | "-DprojectUnselectedLibAttrValue=new-value"
        "changing included library variant metadata"           | "-DcompositeLibAttrValue=new-value"
        "changing external library variant metadata"           | "-DexternalLibAttrValue=new-value"
    }

    private void withOriginalSourceIn(String basePath) {
        sourceFileIn(basePath).tap {
            text = """
                class Main {}
            """.stripIndent()
            makeOlder()
        }
    }

    private void withChangedSourceIn(String basePath) {
        sourceFileIn(basePath).text = """
            class Main {
                public static void main(String[] args) {}
            }
        """.stripIndent()
    }

    private TestFile sourceFileIn(String basePath) {
        return file("$basePath/src/main/java/Main.java")
    }

    private void withNewExternalDependency() {
        buildFile << """
            dependencies { implementation 'org.external:external-tool:1.0' }
        """
    }
}
