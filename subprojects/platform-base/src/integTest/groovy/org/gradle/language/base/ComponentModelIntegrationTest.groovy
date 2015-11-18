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
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.util.Matchers.containsText

class ComponentModelIntegrationTest extends AbstractComponentModelIntegrationTest {

    def "setup"() {
        withCustomComponentType()
        buildFile << """
            model {
                components {
                    main(CustomComponent)
                }
            }
        """
    }

    void withMainSourceSet() {
        withCustomLanguageType()
        buildFile << """
            model {
                components {
                    main {
                        sources {
                            someLang(CustomLanguageSourceSet)
                        }
                    }
                }
            }
        """
    }

    void withBinaries() {
        withCustomBinaryType()
        buildFile << """
            class ComponentBinaryRules extends RuleSource {
                @ComponentBinaries
                void addBinaries(ModelMap<CustomBinary> binaries, CustomComponent component) {
                    binaries.create("b1", CustomBinary)
                    binaries.create("b2", CustomBinary)
                }
            }

            apply type: ComponentBinaryRules

            model {
                components {
                    test(CustomComponent)
                }
            }
        """
    }

    void withLanguageTransforms() {
        withMainSourceSet()
        withCustomLanguageTransform()
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
        output.contains """beforeEach CustomComponent 'main'
afterEach CustomComponent 'main'
beforeEach CustomComponent 'newComponent'
creating CustomComponent 'newComponent'
afterEach CustomComponent 'newComponent'"""

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
        output.contains """beforeEach CustomComponent 'main'
afterEach CustomComponent 'main'
beforeEach CustomComponent 'newComponent'
creating CustomComponent 'newComponent'
afterEach CustomComponent 'newComponent'"""

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
                    bar(nodeValue: "CustomLanguageSourceSet 'main:bar'")
                    someLang(nodeValue: "CustomLanguageSourceSet 'main:someLang'")
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
        failure.assertThatCause(containsText("Cannot create a 'DefaultCustomComponent' because this type is not known to components. Known types are: CustomComponent"))
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
        failure.assertThatCause(containsText("Cannot create a 'AnotherCustomComponent' because this type is not known to components. Known types are: CustomComponent"))
    }

    def "reasonable error message when creating binary with default implementation"() {
        given:
        withBinaries()

        when:
        buildFile << """
        model {
            components {
                main {
                    binaries {
                        another(DefaultCustomBinary)
                    }
                }
            }
        }

        """
        then:
        fails "model"

        and:
        failure.assertThatCause(containsText("Cannot create a 'DefaultCustomBinary' because this type is not known to binaries. Known types are: CustomBinary"))
    }

    def "reasonable error message when creating binary with no implementation"() {
        given:
        withBinaries()

        when:
        buildFile << """
        interface AnotherCustomBinary extends BinarySpec {}

        model {
            components {
                main {
                    binaries {
                        another(AnotherCustomBinary)
                    }
                }
            }
        }

        """
        then:
        fails "model"

        and:
        failure.assertThatCause(containsText("Cannot create a 'AnotherCustomBinary' because this type is not known to binaries. Known types are: CustomBinary"))
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
                            sources()
                            tasks()
                        }
                        test {
                            sources()
                            tasks()
                        }
                    }
                    sources()
                }
                test {
                    binaries {
                        main {
                            sources()
                            tasks()
                        }
                        test {
                            sources()
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
                        def binaries = $.components.main.binaries
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
        output.contains "names: [b1, b2]"
    }

    def "can reference binaries container elements using specialized type in a rule"() {
        given:
        withBinaries()
        buildFile << '''
            class TaskRules extends RuleSource {
                @Mutate
                void addPrintSourceDisplayNameTask(ModelMap<Task> tasks, @Path("components.main.binaries.b1") CustomBinary binary) {
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
                        def tasks = $.components.main.binaries.b1.tasks
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
        output.contains "names: [customMainB1MainSomeLang]"
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

    @Issue("android problem with 2.8-rc-1")
    def "plugin can declare a top level element and register a component type"() {
        buildFile << """
            @Managed
            interface MyModel {
                String value
            }

            // Define some binary and component types.
            interface SampleBinarySpec extends BinarySpec {}
            class DefaultSampleBinary extends BaseBinarySpec implements SampleBinarySpec {}

            interface SampleComponent extends ComponentSpec{}
            class DefaultSampleComponent extends BaseComponentSpec implements SampleComponent {}

            class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.plugins.apply(ComponentModelBasePlugin)
                }

                static class Rules extends RuleSource {
                    @Model
                    void createManagedModel(MyModel value) {
                        println "createManagedModel"
                    }

                    @ComponentType
                    void registerComponent(ComponentTypeBuilder<SampleComponent> builder) {
                        println "registerComponent"
                        builder.defaultImplementation(DefaultSampleComponent)
                    }
                }

            }

            apply plugin: MyPlugin
        """

        expect:
        succeeds "components"
    }

    @Issue("android problem with 2.8-rc-1")
    def "plugin can declare a top level element and register a component type and use the JavaBasePlugin"() {
        buildFile << """
            @Managed
            public interface MyModel {
            }

            // Define some binary and component types.
            interface SampleBinarySpec extends BinarySpec {}
            class DefaultSampleBinary extends BaseBinarySpec implements SampleBinarySpec {}

            interface SampleComponent extends ComponentSpec{}
            class DefaultSampleComponent extends BaseComponentSpec implements SampleComponent {}

            class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getPlugins().apply(ComponentModelBasePlugin.class);
                    project.getPlugins().apply(JavaBasePlugin.class);
                }

                static class Rules extends RuleSource {
                    @Model("android")
                    public void createManagedModel(MyModel value) {
                        println "createManagedModel"
                    }

                    @ComponentType
                    void registerComponent(ComponentTypeBuilder<SampleComponent> builder) {
                        println "registerComponent"
                        builder.defaultImplementation(DefaultSampleComponent)
                    }
                }

            }

            apply plugin: MyPlugin
        """
        expect:
        succeeds "components"
    }
}
