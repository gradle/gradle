/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.attributes

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.attributes.Attribute
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Integration tests that verify attributes can always be retrieved from a resolved variants' attributes.
 */
@Issue("https://github.com/gradle/gradle/issues/28695")
final class AttributeCoercionIntegrationTest extends AbstractIntegrationSpec {
    def "new attribute type created by producer, used by consumer works"() {
        given:
        settingsFile("""
            include 'consumer', 'producer'
        """)

        and: "a producer that adds a new attribute type and a variant using it"
        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            interface MyAttributeType extends Named {}

            def MY_ATTRIBUTE_NAME = "myAttribute"
            Attribute<MyAttributeType> ATTRIBUTE_TYPE = Attribute.of(MY_ATTRIBUTE_NAME, MyAttributeType.class)

            configurations {
                consumable("myVariant") {
                    attributes {
                        attribute(ATTRIBUTE_TYPE, project.objects.named(MyAttributeType.class, 'myValue'))
                    }
                    outgoing.artifact(file("output.txt"))
                }
            }
        """)

        and: "a consumer build can resolve a variant of the producer using the new attribute type"
        buildFile("consumer/build.gradle", """
            interface MyAttributeType extends Named {}

            def MY_ATTRIBUTE_NAME = "myAttribute"
            Attribute<MyAttributeType> ATTRIBUTE_TYPE = Attribute.of(MY_ATTRIBUTE_NAME, MyAttributeType.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))

                    attributes {
                        $attributeCreationLogic
                    }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            ${defineResolveTask()}
        """)

        expect: "the resolution succeeds (and the attribute value used to resolve can be retrieved from the resolved variant)"
        assertResolvedWithAttributePresent()

        where:
        attributeCreationLogic << [
            "attribute(ATTRIBUTE_TYPE, project.objects.named(MyAttributeType.class, 'myValue'))",
            "attribute(Attribute.of('myAttribute', String), 'myValue')",
        ]
    }

    def "new attribute type created by producer as an included build, used by consumer works"() {
        given:
        settingsFile("""
            includeBuild 'producer'
            include 'consumer'
        """)

        and: "a producer that adds a new attribute type; a variant using it, and a resolvable configuration for it"
        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            interface MyAttributeType extends Named {}

            def MY_ATTRIBUTE_NAME = "myAttribute"
            Attribute<MyAttributeType> ATTRIBUTE_TYPE = Attribute.of(MY_ATTRIBUTE_NAME, MyAttributeType.class)

            configurations {
                consumable("myVariant") {
                    attributes {
                        attribute(ATTRIBUTE_TYPE, objects.named(MyAttributeType.class, 'myValue'))
                    }
                    outgoing.artifact(file("output.txt"))
                }
            }

            group = 'org.gradle.example'
            version = '1.0'
        """)

        and: "a consumer build can resolve a variant of the producer using the new attribute type"
        buildFile("consumer/build.gradle", """
            interface MyAttributeType extends Named {}

            def MY_ATTRIBUTE_NAME = "myAttribute"
            Attribute<MyAttributeType> ATTRIBUTE_TYPE = Attribute.of(MY_ATTRIBUTE_NAME, MyAttributeType.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))

                    attributes {
                        $attributeCreationLogic
                    }
                }
            }

            dependencies {
                myDeps("org.gradle.example:producer:1.0")
            }

            ${defineResolveTask()}
        """)

        expect: "the resolution succeeds (and the attribute value used to resolve can be retrieved from the resolved variant)"
        assertResolvedWithAttributePresent()

        where:
        attributeCreationLogic << [
            "attribute(ATTRIBUTE_TYPE, objects.named(MyAttributeType.class, 'myValue'))",
            "attribute(Attribute.of('myAttribute', String), 'myValue')",
        ]
    }

    def "new attribute type created by producer via plugin, used by consumer works"() {
        given: "a plugin that adds a new attribute type; a variant using it, and a resolvable configuration for it"
        definePluginBuild()

        and:
        settingsFile("""
            pluginManagement {
                includeBuild 'build-logic'
            }
            include 'consumer', 'producer'
        """)

        and: "a producer build that uses the plugin"
        file("producer/output.txt") << "sample output"
        buildFile("producer/build.gradle", """
            plugins {
                id 'org.gradle.example.myPlugin'
            }
        """)

        and: "a consumer build can resolve a variant of the producer using the new attribute type"
        buildFile("consumer/build.gradle", """
            interface MyAttributeType extends Named {}

            def MY_ATTRIBUTE_NAME = "myAttribute"
            Attribute<MyAttributeType> ATTRIBUTE_TYPE = Attribute.of(MY_ATTRIBUTE_NAME, MyAttributeType.class)

            configurations {
                dependencyScope("myDeps")
                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))

                    attributes {
                        $attributeCreationLogic
                    }
                }
            }

            dependencies {
                myDeps(project(":producer"))
            }

            ${defineResolveTask()}
        """)

        expect: "the resolution succeeds (and the attribute value used to resolve can be retrieved from the resolved variant)"
        assertResolvedWithAttributePresent()

        where:
        attributeCreationLogic << [
            "attribute(ATTRIBUTE_TYPE, project.objects.named(MyAttributeType.class, 'myValue'))",
            "attribute(Attribute.of('myAttribute', String), 'myValue')",
        ]
    }

    def "new attribute type created by producer via plugin, used by a consumer using the attribute type imported from the same plugin works"() {
        given: "a plugin that adds a new attribute type; a variant using it, and a resolvable configuration for it"
        definePluginBuild()

        and:
        settingsFile("""
            pluginManagement {
                includeBuild 'build-logic'
            }
            include 'consumer', 'producer'
        """)

        and: "a producer build that uses the plugin"
        buildFile("producer/build.gradle", """
            plugins {
                id 'org.gradle.example.myPlugin'
            }
        """)

        and: "a consumer build can resolve a variant of the producer using the new attribute type using the configuration added by the plugin"
        buildFile("consumer/build.gradle", """
            import org.gradle.example.MyAttributeType

            plugins {
                id 'org.gradle.example.myPlugin'
            }

            def MY_ATTRIBUTE_NAME = "myAttribute";
            def ATTRIBUTE_TYPE = Attribute.of(MY_ATTRIBUTE_NAME, MyAttributeType.class);

            dependencies {
                myDeps(project(":producer"))
            }

            ${defineResolveTask()}
        """)

        expect: "the resolution succeeds as expected and the attribute value used to resolve can be retrieved from the resolved variant"
        assertResolvedWithAttributePresent()
    }

    def "new attribute type created by a plugin applied to an included producer, and an included consumer project which resolves it works"() {
        given: "a plugin that adds a new attribute type; a variant using it, and a resolvable configuration for it"
        definePluginBuild()

        and:
        settingsFile("""
            includeBuild 'producer'
            includeBuild 'consumer'
        """)

        and: "an consumer build that uses the plugin and depends on an included producer build"
        buildFile("consumer/settings.gradle", """
            includeBuild '../producer'
        """)
        buildFile("consumer/build.gradle", """
            public interface MyAttributeType extends Named {}
            def MY_ATTRIBUTE_NAME = "myAttribute"
            Attribute<MyAttributeType> ATTRIBUTE_TYPE = Attribute.of(MY_ATTRIBUTE_NAME, MyAttributeType.class)

            configurations {
                dependencyScope("myDeps")

                resolvable("myResolver") {
                    extendsFrom(configurations.getByName("myDeps"))
                    attributes {
                        attribute(ATTRIBUTE_TYPE, objects.named(MyAttributeType.class, 'myValue'))
                    }
                }
            }

            dependencies {
                myDeps("org.gradle.example:producer:1.0")
            }

            ${defineResolveTask()}
        """)

        and: "a producer build that uses the plugin"
        buildFile("producer/settings.gradle", """
            pluginManagement {
                includeBuild '../build-logic'
            }
        """)
        buildFile("producer/build.gradle", """
            plugins {
                id 'org.gradle.example.myPlugin'
            }

            group = 'org.gradle.example'
            version = '1.0'
        """)

        expect: "the resolution succeeds as expected and the attribute value used to resolve can be retrieved from the resolved variant"
        assertResolvedWithAttributePresent()
    }

    private void definePluginBuild() {
        file("producer/output.txt") << "sample output"
        javaFile("build-logic/src/main/java/org/gradle/example/MyPlugin.java", """
            package org.gradle.example;

            import ${Attribute.name};
            import ${DependencyScopeConfiguration.name};
            import ${NamedDomainObjectProvider.name};
            import ${Plugin.name};
            import ${Project.name};

            public abstract class MyPlugin implements Plugin<Project> {
                private static final String MY_ATTRIBUTE_NAME = "myAttribute";
                private static final Attribute<MyAttributeType> ATTRIBUTE_TYPE = Attribute.of(MY_ATTRIBUTE_NAME, MyAttributeType.class);

                public void apply(Project project) {
                    project.getConfigurations().consumable("myVariant", c -> {
                        c.getAttributes().attribute(ATTRIBUTE_TYPE, project.getObjects().named(MyAttributeType.class, "myValue"));
                        c.getOutgoing().artifact(project.file("output.txt"));
                    });

                    NamedDomainObjectProvider<DependencyScopeConfiguration> myDeps = project.getConfigurations().dependencyScope("myDeps");

                    project.getConfigurations().resolvable("myResolver", c -> {
                        c.extendsFrom(myDeps.get());
                        c.getAttributes().attribute(ATTRIBUTE_TYPE, project.getObjects().named(MyAttributeType.class, "myValue"));
                    });
                }
            }
        """)
        javaFile("build-logic/src/main/java/org/gradle/example/MyAttributeType.java", """
            package org.gradle.example;

            import ${Named.name};

            public interface MyAttributeType extends Named {}
        """)

        buildFile("build-logic/build.gradle", """
            plugins {
                id 'java-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    myPlugin {
                        id = 'org.gradle.example.myPlugin'
                        implementationClass = 'org.gradle.example.MyPlugin'
                    }
                }
            }
        """)
    }

    private String defineResolveTask(String configurationName = "myResolver") {
        return """
            tasks.register("resolve") {
                def myFiles = configurations.$configurationName
                    .incoming.artifactView {}
                    .artifacts
                    .resolvedArtifacts
                    .map { resolvedArtifactResults ->
                        resolvedArtifactResults.each { r ->
                            println("Attribute type: " + r.variant.attributes.getAttribute(ATTRIBUTE_TYPE))
                        }
                    }

                inputs.files(configurations.$configurationName)
                doLast {
                    myFiles.get().each { println("Resolved: " + it.file.name) }
                }
            }
        """
    }

    private void assertResolvedWithAttributePresent() {
        succeeds(":consumer:resolve")
        outputContains("Resolved: output.txt")
        outputContains("Attribute type: myValue")
    }
}
