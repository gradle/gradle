/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base
import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.util.TextUtil
import spock.lang.Unroll

import static org.gradle.util.Matchers.containsText

class ComponentModelIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        EnableModelDsl.enable(executer)
        buildScript """
            interface CustomComponent extends ComponentSpec {}
            class DefaultCustomComponent extends BaseComponentSpec implements CustomComponent {}

            class ComponentTypeRules extends RuleSource {
                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomComponent> builder) {
                    builder.defaultImplementation(DefaultCustomComponent)
                }
            }

            apply type: ComponentTypeRules

            model {
                components {
                    main(CustomComponent)
                }
            }
        """
    }

    void withCustomLanguage() {
        buildFile << """
            interface CustomLanguageSourceSet extends LanguageSourceSet {
                String getData();
            }
            class DefaultCustomLanguageSourceSet extends BaseLanguageSourceSet implements CustomLanguageSourceSet {
                final String data = "foo"

                public boolean getMayHaveSources() {
                    true
                }
            }

            class LanguageTypeRules extends RuleSource {
                @LanguageType
                void registerCustomLanguage(LanguageTypeBuilder<CustomLanguageSourceSet> builder) {
                    builder.defaultImplementation(DefaultCustomLanguageSourceSet)
                }
            }

            apply type: LanguageTypeRules
        """
    }

    void withMainSourceSet() {
        withCustomLanguage()
        buildFile << """
            model {
                components {
                    main {
                        sources {
                            main(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """
    }

    void withBinaries() {
        buildFile << """
            interface CustomBinary extends BinarySpec {
                String getData();
            }
            class DefaultCustomBinary extends BaseBinarySpec implements CustomBinary {
                final String data = "bar"
            }

            class BinaryRules extends RuleSource {
                @BinaryType
                void registerCustomBinary(BinaryTypeBuilder<CustomBinary> builder) {
                    builder.defaultImplementation(DefaultCustomBinary)
                }

                @ComponentBinaries
                void addBinaries(ModelMap<CustomBinary> binaries, CustomComponent component) {
                    binaries.create("main", CustomBinary)
                    binaries.create("test", CustomBinary)
                }
            }

            apply type: BinaryRules

            model {
                components {
                    test(CustomComponent)
                }
            }
        """
    }

    void withLanguageTransforms() {
        withMainSourceSet()
        buildFile << """
            import org.gradle.language.base.internal.registry.*
            import org.gradle.language.base.internal.*
            import org.gradle.language.base.*
            import org.gradle.internal.reflect.*
            import org.gradle.internal.service.*


            class CustomLanguageTransformation implements LanguageTransform {
                Class getSourceSetType() {
                    CustomLanguageSourceSet
                }

                Class getOutputType() {
                    CustomTransformationFileType
                }

                Map<String, Class<?>> getBinaryTools() {
                    throw new UnsupportedOperationException()
                }

                SourceTransformTaskConfig getTransformTask() {
                    new SourceTransformTaskConfig() {
                        String getTaskPrefix() {
                            "custom"
                        }

                        Class<? extends DefaultTask> getTaskType() {
                            DefaultTask
                        }

                        void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                        }
                    }
                }

                boolean applyToBinary(BinarySpec binary) {
                    true
                }
            }

            class CustomTransformationFileType implements TransformationFileType {
            }


            class LanguageRules extends RuleSource {
                @Mutate
                void registerLanguageTransformation(LanguageTransformContainer transforms) {
                    transforms.add(new CustomLanguageTransformation())
                }
            }

            apply type: LanguageRules
        """
    }

    def "component container is visible to rules as various types"() {
        buildFile << """
class Rules extends RuleSource {
    @Defaults
    void verifyAsContainer(ComponentSpecContainer c) {
        assert c.toString() == "ComponentSpecContainer 'components'"
        assert c.withType(CustomComponent).toString() == "ModelMap<CustomComponent> 'components'"
        assert !(c.withType(CustomComponent) instanceof ComponentSpecContainer)
    }

    @Defaults
    void verifyAsModelMap(ModelMap<ComponentSpec> c) {
        assert c.toString() == "ModelMap<ComponentSpec> 'components'"
        assert c.withType(CustomComponent).toString() == "ModelMap<CustomComponent> 'components'"
        assert !(c instanceof ComponentSpecContainer)
    }

    @Defaults
    void verifyAsSpecializedModelMap(ModelMap<CustomComponent> c) {
        assert c.toString() == "ModelMap<CustomComponent> 'components'"
        assert !(c instanceof ComponentSpecContainer)
    }

    @Defaults
    void verifyAsCollectionBuilder(CollectionBuilder<ComponentSpec> c) {
//        assert c.toString() == "CollectionBuilder<ComponentSpec> 'components'"
        assert !(c instanceof ComponentSpecContainer)
    }

    @Defaults
    void verifyAsSpecializedCollectionBuilder(CollectionBuilder<CustomComponent> c) {
//        assert c.toString() == "CollectionBuilder<CustomComponent> 'components'"
        assert !(c instanceof ComponentSpecContainer)
    }
}

apply plugin: Rules

model {
    components {
        assert it.toString() == "ComponentSpecContainer 'components'"
        assert it instanceof ComponentSpecContainer
    }
}
"""

        expect:
        succeeds 'tasks'
    }

    def "component sources and binaries containers are visible in model report"() {
        when:
        succeeds "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                main {
                    binaries()
                    sources()
                }
            }
        }
    }

    def "can reference sources container for a component in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceNames") {
                        def sources = $("components.main.sources")
                        doLast {
                            println "names: ${sources.values()*.name}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceNames"

        then:
        output.contains "names: [main]"
    }

    def "component sources container elements are visible in model report"() {
        given:
        withMainSourceSet()
        buildFile << """
            model {
                components {
                    main {
                        sources {
                            test(CustomLanguageSourceSet)
                        }
                    }
                    test(CustomComponent) {
                        sources {
                            test(CustomLanguageSourceSet)
                        }
                    }
                    foo(CustomComponent) {
                        sources {
                            bar(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """

        when:
        succeeds "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                foo {
                    binaries()
                    sources {
                        bar(nodeValue: "DefaultCustomLanguageSourceSet 'foo:bar'")
                    }
                }
                main {
                    binaries()
                    sources {
                        main()
                        test()
                    }
                }
                test {
                    binaries()
                    sources {
                        test()
                    }
                }
            }
        }
    }

    def "can reference sources container elements in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            model {
                tasks {
                    create("printSourceDisplayName") {
                        def sources = $("components.main.sources.main")
                        doLast {
                            println "sources display name: ${sources.displayName}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printSourceDisplayName"

        then:
        output.contains "sources display name: DefaultCustomLanguageSourceSet 'main:main'"
    }

    def "can reference sources container elements using specialized type in a rule"() {
        given:
        withMainSourceSet()
        buildFile << '''
            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(ModelMap<Task> tasks, @Path("components.main.sources.main") CustomLanguageSourceSet sourceSet) {
                    tasks.create("printSourceData") {
                        doLast {
                            println "sources data: ${sourceSet.data}"
                        }
                    }
                }
            }

            apply type: TaskRules
        '''

        when:
        succeeds "printSourceData"

        then:
        output.contains "sources data: foo"
    }

    def "cannot remove source sets"() {
        given:
        withMainSourceSet()
        buildFile << '''
            class SourceSetRemovalRules extends RuleSource {
                @Mutate
                void clearSourceSets(@Path("components.main.sources") NamedDomainObjectCollection<LanguageSourceSet> sourceSets) {
                    sourceSets.clear()
                }

                @Mutate
                void closeMainComponentSourceSetsForTasks(ModelMap<Task> tasks, @Path("components.main.sources") NamedDomainObjectCollection<LanguageSourceSet> sourceSets) {
                }
            }

            apply type: SourceSetRemovalRules
        '''

        when:
        fails()

        then:
        failureHasCause("This collection does not support element removal.")
    }

    def "plugin can create component"() {
        when:
        buildFile << """
        class SomeComponentPlugin extends RuleSource {
            @Mutate
            void createComponent(ComponentSpecContainer specs) {
                specs.create("someCustomComponent", CustomComponent)
            }
        }
        apply plugin: SomeComponentPlugin
        """
        then:
        succeeds "model"

        and:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                main {
                    binaries()
                    sources()
                }
                someCustomComponent {
                    binaries()
                    sources()
                }
            }
        }
    }

    def "plugin can configure component with given name"() {
        given:
        withMainSourceSet()
        when:
        buildFile << """
        class SomeComponentPlugin extends RuleSource {
            @Mutate
            void addSourceSet(ComponentSpecContainer specs) {
                specs.named("main") {
                    sources {
                        bar(CustomLanguageSourceSet)
                    }
                }

            }
        }
        apply plugin: SomeComponentPlugin
        """
        then:
        succeeds "model"

        and:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                main {
                    binaries()
                    sources {
                        bar()
                        main()
                    }
                }
            }

        }
    }

    def "plugin can apply component beforeEach / afterEach"() {
        when:
        buildFile << """
        class SomeComponentPlugin extends RuleSource {
            @Mutate
            void applyCustomizations(ComponentSpecContainer specs) {
                specs.create("newComponent", CustomComponent) {
                    println "creating \$it"
                }
                specs.afterEach {
                    println "afterEach \$it"
                }
                specs.beforeEach {
                    println "beforeEach \$it"
                }
            }
        }
        apply plugin: SomeComponentPlugin
        """
        then:
        succeeds "tasks"

        and:
        output.contains(TextUtil.toPlatformLineSeparators("""beforeEach DefaultCustomComponent 'main'
afterEach DefaultCustomComponent 'main'
beforeEach DefaultCustomComponent 'newComponent'
creating DefaultCustomComponent 'newComponent'
afterEach DefaultCustomComponent 'newComponent'"""))

    }

    def "plugin can configure component with given type "() {
        given:
        withMainSourceSet()
        when:
        buildFile << """
        class SomeComponentPlugin extends RuleSource {
            @Mutate
            void applyCustomizations(ComponentSpecContainer specs) {
                specs.withType(CustomComponent) {
                    sources {
                        bar(CustomLanguageSourceSet)
                    }
                }
            }
        }
        apply plugin: SomeComponentPlugin
        """
        then:
        succeeds "model"

        and:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                main {
                    binaries()
                    sources {
                        bar()
                        main()
                    }
                }
            }
        }
    }

    def "buildscript can create component"() {
        when:
        buildFile << """
        model {
            components {
                someCustomComponent(CustomComponent)
            }
        }
        """
        then:
        succeeds "model"

        and:
        ModelReportOutput.from(output).hasNodeStructure {
            someCustomComponent {
                binaries()
                sources()
            }
        }

    }

    def "buildscript can configure component with given name"() {
        given:
        withMainSourceSet()
        when:
        buildFile << """
        model {
            components {
                test(CustomComponent)

                named("main") {
                    sources {
                        bar(CustomLanguageSourceSet)
                    }
                }
            }
        }
        """
        then:
        succeeds "model"

        and:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                main {
                    binaries()
                    sources {
                        bar()
                        main()
                    }
                }
                test {
                    binaries()
                    sources()
                }
            }
        }
    }

    def "buildscript can apply component beforeEach / afterEach"() {
        given:
        withMainSourceSet()
        when:
        buildFile << """
        model {
            components {
               newComponent(CustomComponent){
                    println "creating \$it"
               }
               beforeEach {
                    println "beforeEach \$it"
               }

               afterEach {
                    println "afterEach \$it"
               }
            }
        }
        """
        then:
        succeeds "tasks"

        and:
        output.contains(TextUtil.toPlatformLineSeparators("""beforeEach DefaultCustomComponent 'main'
afterEach DefaultCustomComponent 'main'
beforeEach DefaultCustomComponent 'newComponent'
creating DefaultCustomComponent 'newComponent'
afterEach DefaultCustomComponent 'newComponent'"""))

    }

    def "buildscript can configure component with given type "() {
        given:
        withMainSourceSet()
        when:
        buildFile << """
        model {
            components {
                withType(CustomComponent.class) {
                    sources {
                        bar(CustomLanguageSourceSet)
                    }
                }
            }
        }
        """
        then:
        succeeds "model"

        and:
        ModelReportOutput.from(output).hasNodeStructure {
            main {
                binaries()
                sources {
                    bar(nodeValue: "DefaultCustomLanguageSourceSet 'main:bar'")
                    main(nodeValue: "DefaultCustomLanguageSourceSet 'main:main'")
                }
            }
        }
    }

    def "reasonable error message when creating component with default implementation"() {
        when:
        buildFile << """
        model {
            components {
                another(DefaultCustomComponent)
            }
        }

        """
        then:
        fails "model"

        and:
        failure.assertThatCause(containsText("The model node of type: 'DefaultCustomComponent' can not be constructed. The type must be managed (@Managed) or one of the following types [ModelSet<?>, ManagedSet<?>, ModelMap<?>, List, Set]"))
    }

    def "reasonable error message when creating component with no implementation"() {
        when:
        buildFile << """
        interface AnotherCustomComponent extends ComponentSpec {}

        model {
            components {
                another(AnotherCustomComponent)
            }
        }

        """
        then:
        fails "model"

        and:
        failure.assertThatCause(containsText("The model node of type: 'AnotherCustomComponent' can not be constructed. The type must be managed (@Managed) or one of the following types [ModelSet<?>, ManagedSet<?>, ModelMap<?>, List, Set]"))
    }

    def "componentSpecContainer is groovy decorated when used in rules"() {
        given:
        withMainSourceSet()
        buildFile << '''
            class ComponentSpecContainerRules extends RuleSource {
                @Mutate
                void addComponents(ComponentSpecContainer componentSpecs) {
                    componentSpecs.anotherCustom(CustomComponent) {
                    }
                }

                @Mutate
                void addComponentTasks(TaskContainer tasks, ComponentSpecContainer componentSpecs) {
                    tasks.create("printMainComponent") {
                        doLast{
                            //reference by name
                            println "Main component: " + componentSpecs.main.name
                        }

                    }
                }
            }

            apply type: ComponentSpecContainerRules
        '''

        when:
        succeeds "printMainComponent"
        then:
        output.contains("Main component: main")
    }

    @Unroll
    def "#projectionType is closed when used as input"() {
        given:
        withMainSourceSet()
        buildFile << """
            class ComponentSpecContainerRules extends RuleSource {

                @Mutate
                void addComponentTasks(TaskContainer tasks, $projectionType componentSpecs) {
                    componentSpecs.all {
                        // some stuff here
                    }
                }
            }

            apply type: ComponentSpecContainerRules
        """

        when:
        fails "tasks"
        then:
        failureHasCause "Attempt to mutate closed view of model of type '$fullQualified' given to rule 'ComponentSpecContainerRules#addComponentTasks'"

        where:
        projectionType                     | fullQualified
        "CollectionBuilder<ComponentSpec>" | "org.gradle.model.collection.CollectionBuilder<org.gradle.platform.base.ComponentSpec>"
        "ModelMap<ComponentSpec>"          | "org.gradle.model.ModelMap<org.gradle.platform.base.ComponentSpec>"
        "ComponentSpecContainer"           | "org.gradle.platform.base.ComponentSpecContainer"
    }

    def "component binaries container elements and their tasks containers are visible in model report"() {
        given:
        withBinaries()

        when:
        succeeds "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                main {
                    binaries {
                        main {
                            tasks()
                        }
                        test {
                            tasks()
                        }
                    }
                    sources()
                }
                test {
                    binaries {
                        main {
                            tasks()
                        }
                        test {
                            tasks()
                        }
                    }
                    sources()
                }
            }
        }
    }

    def "can reference binaries container for a component in a rule"() {
        given:
        withBinaries()
        buildFile << '''
            model {
                tasks {
                    create("printBinaryNames") {
                        def binaries = $("components.main.binaries")
                        doLast {
                            println "names: ${binaries.keySet().toList()}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printBinaryNames"

        then:
        output.contains "names: [main, test]"
    }

    def "can reference binaries container elements using specialized type in a rule"() {
        given:
        withBinaries()
        buildFile << '''
            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(ModelMap<Task> tasks, @Path("components.main.binaries.main") CustomBinary binary) {
                    tasks.create("printBinaryData") {
                        doLast {
                            println "binary data: ${binary.data}"
                        }
                    }
                }
            }

            apply type: TaskRules
        '''

        when:
        succeeds "printBinaryData"

        then:
        output.contains "binary data: bar"
    }

    def "can reference task container of a binary in a rule"() {
        given:
        withBinaries()
        withLanguageTransforms()
        buildFile << '''
            model {
                tasks {
                    create("printBinaryTaskNames") {
                        def tasks = $("components.main.binaries.main.tasks")
                        doLast {
                            println "names: ${tasks*.name}"
                        }
                    }
                }
            }
        '''

        when:
        succeeds "printBinaryTaskNames"

        then:
        output.contains "names: [customMainMainMain]"
    }

    def "can view components container as a model map and as a collection builder"() {
        given:
        buildFile << '''
            class ComponentsRules extends RuleSource {
                @Mutate
                void addViaCollectionBuilder(@Path("components") CollectionBuilder<ComponentSpec> components) {
                    components.create("viaCollectionBuilder", CustomComponent)
                }

                @Mutate
                void addViaModelMap(@Path("components") ModelMap<ComponentSpec> components) {
                    components.create("viaModelMap", CustomComponent)
                }

                @Mutate
                void addPrintComponentNamesTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("printComponentNames") {
                        doLast {
                            println "component names: ${components.values()*.name}"
                        }
                    }
                }
            }

            apply type: ComponentsRules
        '''

        when:
        succeeds "printComponentNames"

        then:
        output.contains "component names: [main, viaCollectionBuilder, viaModelMap]"
    }
}
