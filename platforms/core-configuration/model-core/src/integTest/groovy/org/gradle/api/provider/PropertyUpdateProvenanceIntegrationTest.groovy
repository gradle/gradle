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

class PropertyUpdateProvenanceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument("-Dorg.gradle.internal.property-provenance=true")
        settingsFile << "rootProject.name = 'updates'"
        file("buildSrc/build.gradle") << """
            plugins { id 'java-gradle-plugin' }
            gradlePlugin {
                plugins {
                    defaults { id = 'example.defaults'; implementationClass = 'example.DefaultsPlugin' }
                    updates { id = 'example.updates'; implementationClass = 'example.UpdatesPlugin' }
                }
            }
        """
        file("buildSrc/src/main/java/example/DefaultsPlugin.java") << """
            package example;

            import java.util.concurrent.atomic.AtomicInteger;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.Property;

            public class DefaultsPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    Property<String> value = project.getObjects().property(String.class);
                    AtomicInteger evaluations = new AtomicInteger();
                    value.convention("fallback");
                    value.set(project.getProviders().provider(() -> {
                        evaluations.incrementAndGet();
                        return (String) project.findProperty("sampleValue");
                    }));
                    project.getExtensions().add("updateValue", value);
                    project.getTasks().register("showUpdateProvenance", task -> task.doLast(ignored -> {
                        try {
                            System.out.println("result=" + value.get());
                        } finally {
                            System.out.println("source evaluations=" + evaluations.get());
                        }
                    }));
                }
            }
        """
        file("buildSrc/src/main/java/example/UpdatesPlugin.java") << """
            package example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.internal.provider.DefaultProperty;

            public class UpdatesPlugin implements Plugin<Project> {
                @SuppressWarnings("unchecked")
                public void apply(Project project) {
                    project.getPluginManager().apply("example.defaults");
                    // replace() is an existing internal seam, not a new public Property API.
                    DefaultProperty<String> value = (DefaultProperty<String>) project.getExtensions().getByName("updateValue");
                    value.replace(previous -> previous.map(String::trim).map(String::toUpperCase));
                }
            }
        """
        buildFile << """
            plugins { id 'example.updates' }
            updateValue.replace { previous -> previous.map { it + '!' } }
        """
    }

    def "plugin and script map updates retain a source trace through #boundary"() {
        buildFile << configuration

        when:
        fails("showUpdateProvenance")

        then:
        failure.assertHasCause("""Cannot query the value of this property because it has no value available.
Failure trace to source:
    at task ':showUpdateProvenance' action [get()]
    at build file 'build.gradle' [map update]
    at plugin 'example.updates' [map update]
    at plugin 'example.defaults' [explicit source]""")
        failure.error.contains("Shadowed configuration:")
        failure.error.contains("at plugin 'example.defaults' [convention]")
        outputContains("source evaluations=1")

        where:
        boundary           | configuration
        "ordinary query"   | ""
        "finalization"     | "updateValue.finalizeValue()"
        "finalize on read" | "updateValue.finalizeValueOnRead()"
    }

    def "deferred update keeps the registering plugin identity and its separate occurrence"() {
        def plugin = file("buildSrc/src/main/java/example/UpdatesPlugin.java")
        plugin.text = plugin.text.replace(
            "value.replace(previous -> previous.map(String::trim).map(String::toUpperCase));",
            """
                value.replace(previous -> previous.map(String::trim).map(String::toUpperCase));
                project.getTasks().named("showUpdateProvenance").configure(task ->
                    value.replace(previous -> previous.map(text -> text + "?"))
                );
            """
        )

        when:
        fails("showUpdateProvenance")

        then:
        failure.assertHasCause("""Cannot query the value of this property because it has no value available.
Failure trace to source:
    at task ':showUpdateProvenance' action [get()]
    at plugin 'example.updates' [map update]
    at build file 'build.gradle' [map update]
    at plugin 'example.updates' [map update]
    at plugin 'example.defaults' [explicit source]""")
        outputContains("source evaluations=1")
    }

    def "ordinary replacement cuts all prior updates and does not evaluate the discarded source"() {
        buildFile << "updateValue.set(providers.provider { null })"

        when:
        fails("showUpdateProvenance")

        then:
        failure.error.contains("at build file 'build.gradle' [explicit source]")
        !failure.error.contains("[map update]")
        outputContains("source evaluations=0")
    }

    def "successful updates are silent and evaluate the source once"() {
        when:
        succeeds("showUpdateProvenance", "-PsampleValue= hello ")

        then:
        outputContains("result=HELLO!")
        outputContains("source evaluations=1")
        !output.contains("Failure trace to source")
        !output.contains("[map update]")
    }

    def "disabled updates preserve the original missing-value cause"() {
        executer.withArgument("-Dorg.gradle.internal.property-provenance=false")

        when:
        fails("showUpdateProvenance")

        then:
        failure.assertHasCause("Cannot query the value of this property because it has no value available.")
        !failure.error.contains("Failure trace to source")
        !failure.error.contains("Shadowed configuration")
        outputContains("source evaluations=1")
    }
}
