/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.Matchers.containsString

class CrossProjectPropertyProvenanceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument("-Dorg.gradle.internal.property-provenance=true")
    }

    def "cross-project binding retains #origin across #boundary"() {
        crossProjectBuild()
        file(script) << wiring

        when:
        fails(":consumer:crossValue")

        then:
        failure.assertHasCause(missingMessage() + """
Failure trace to source:
    at task ':consumer:crossValue' action [get()]
    at $origin [explicit source]
    at plugin 'example.producer' [convention]

Shadowed configuration:
    at plugin 'example.consumer' [convention]

Trace limitations:
    Upstream provenance is unavailable beyond an opaque or unsupported provider boundary.""")
        output.count("SOURCE_EVALUATED") == 1

        where:
        boundary                 | origin                            | script                       | wiring
        "root script"            | "build file 'build.gradle.kts'"    | "build.gradle.kts"           | 'project(":consumer").extensions.getByType<example.Values>().getValue().set(project(":producer").extensions.getByType<example.Values>().getValue())'
        "sibling script"         | "build file 'producer/build.gradle.kts'" | "producer/build.gradle.kts" | 'project(":consumer").extensions.getByType<example.Values>().getValue().set(extensions.getByType<example.Values>().getValue())'
        "deferred root plugin"   | "plugin 'example.cross-project'"   | "build.gradle.kts"           | 'apply(plugin = "example.cross-project"); project(":consumer").pluginManager.apply("base")'
    }

    def "root script replacement after plugin application restores the script origin"() {
        crossProjectBuild()
        file("build.gradle.kts") << '''
            apply(plugin = "example.cross-project")
            project(":consumer").pluginManager.apply("base")
            project(":consumer").extensions.getByType<example.Values>().getValue()
                .set(providers.gradleProperty("missing-root-replacement"))
        '''

        when:
        fails(":consumer:crossValue")

        then:
        failure.assertThatCause(containsString("""Failure trace to source:
    at task ':consumer:crossValue' action [get()]
    at build file 'build.gradle.kts' [explicit source]

Shadowed configuration:
    at plugin 'example.consumer' [convention]"""))
        !output.contains("SOURCE_EVALUATED")
    }

    def "finalized cross-project chain retains sources after upstream replacement"() {
        crossProjectBuild()
        file("build.gradle.kts") << '''
            apply(plugin = "example.cross-project")
            project(":consumer").pluginManager.apply("base")
            project(":consumer").extensions.getByType<example.Values>().getValue().finalizeValue()
            project(":producer").extensions.getByType<example.Values>().getValue().set("replacement")
        '''

        when:
        fails(":consumer:crossValue")

        then:
        failure.assertThatCause(containsString("""Failure trace to source:
    at task ':consumer:crossValue' action [get()]
    at plugin 'example.cross-project' [explicit source]
    at plugin 'example.producer' [convention]"""))
        output.count("SOURCE_EVALUATED") == 1
    }

    def "successful cross-project evaluation is silent and evaluates the source once"() {
        crossProjectBuild()
        file("build.gradle.kts") << 'apply(plugin = "example.cross-project"); project(":consumer").pluginManager.apply("base")'

        when:
        succeeds(":consumer:crossValue", "-Pcross-value=present")

        then:
        output.count("SOURCE_EVALUATED") == 1
        !output.contains("Failure trace to source")
        !output.contains("Trace limitations")
    }

    def "disabled cross-project failure preserves the original cause"() {
        crossProjectBuild()
        file("build.gradle.kts") << 'apply(plugin = "example.cross-project"); project(":consumer").pluginManager.apply("base")'
        executer.withArgument("-Dorg.gradle.internal.property-provenance=false")

        when:
        fails(":consumer:crossValue")

        then:
        failure.assertHasCause(missingMessage())
        output.count("SOURCE_EVALUATED") == 1
        !failure.error.contains("Failure trace to source")
    }

    def "independent applications of the same plugin do not mix project traces with parallel execution #parallel"() {
        crossProjectBuild()
        file("settings.gradle.kts") << '\ninclude("other")'
        file("other/build.gradle.kts") << '''
            plugins { id("example.consumer") }
            extensions.getByType<example.Values>().getValue().set(providers.gradleProperty("missing-other"))
        '''
        file("build.gradle.kts") << 'apply(plugin = "example.cross-project"); project(":consumer").pluginManager.apply("base")'

        when:
        fails(":consumer:crossValue", ":other:crossValue", "--continue", parallel ? "--parallel" : "--no-parallel")

        then:
        failure.assertThatCause(containsString("""Failure trace to source:
    at task ':consumer:crossValue' action [get()]
    at plugin 'example.cross-project' [explicit source]
    at plugin 'example.producer' [convention]"""))
        failure.assertThatCause(containsString("""Failure trace to source:
    at task ':other:crossValue' action [get()]
    at build file 'other/build.gradle.kts' [explicit source]

Shadowed configuration:
    at plugin 'example.consumer' [convention]"""))
        output.count("SOURCE_EVALUATED") == 1

        where:
        parallel << [false, true]
    }

    private static String missingMessage() {
        "Cannot query the value of this property because it has no value available."
    }

    private void crossProjectBuild() {
        file("settings.gradle.kts") << '''
            rootProject.name = "cross-project-origins"
            include("producer", "consumer")
        '''
        file("build.gradle.kts") << '''
            plugins {
                id("example.producer") apply false
                id("example.consumer") apply false
                id("example.cross-project") apply false
            }
            evaluationDependsOn(":consumer")
            evaluationDependsOn(":producer")
        '''
        file("producer/build.gradle.kts") << 'plugins { id("example.producer") }\n'
        file("consumer/build.gradle.kts") << 'plugins { id("example.consumer") }\n'
        file("buildSrc/build.gradle.kts") << '''
            plugins { `java-gradle-plugin` }
            gradlePlugin {
                plugins {
                    create("producer") { id = "example.producer"; implementationClass = "example.ProducerPlugin" }
                    create("consumer") { id = "example.consumer"; implementationClass = "example.ConsumerPlugin" }
                    create("cross") { id = "example.cross-project"; implementationClass = "example.CrossPlugin" }
                }
            }
        '''
        file("buildSrc/src/main/java/example/Values.java") << '''
            package example;
            import org.gradle.api.provider.Property;
            public class Values {
                private final Property<String> value;
                public Values(Property<String> value) { this.value = value; }
                public Property<String> getValue() { return value; }
            }
        '''
        file("buildSrc/src/main/java/example/ProducerPlugin.java") << '''
            package example;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.Property;
            public class ProducerPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    Property<String> value = project.getObjects().property(String.class);
                    value.convention(project.provider(() -> {
                        System.out.println("SOURCE_EVALUATED");
                        return project.getProviders().gradleProperty("cross-value").getOrNull();
                    }));
                    project.getExtensions().add("values", new Values(value));
                }
            }
        '''
        file("buildSrc/src/main/java/example/ConsumerPlugin.java") << '''
            package example;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.Property;
            public class ConsumerPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    Property<String> value = project.getObjects().property(String.class);
                    value.convention("consumer default");
                    project.getExtensions().add("values", new Values(value));
                    project.getTasks().register("crossValue", task -> task.doLast(ignored -> value.get()));
                }
            }
        '''
        file("buildSrc/src/main/java/example/CrossPlugin.java") << '''
            package example;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            public class CrossPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    Project consumer = project.project(":consumer");
                    consumer.getPluginManager().withPlugin("base", ignored -> {
                        consumer.getExtensions().getByType(Values.class).getValue().set(
                            project.project(":producer").getExtensions().getByType(Values.class).getValue());
                    });
                }
            }
        '''
    }
}
