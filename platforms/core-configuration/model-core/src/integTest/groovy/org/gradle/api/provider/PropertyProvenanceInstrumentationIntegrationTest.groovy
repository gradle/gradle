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

import static org.hamcrest.Matchers.matchesRegex

/**
 * Call sites baked into build logic by classpath instrumentation, rather than discovered by walking the stack.
 * <p>
 * Every test here runs with only provenance enabled, so the stack walk is off and any location that appears
 * can only have come from an instrumented call site.
 */
class PropertyProvenanceInstrumentationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // Plain provenance: instrumented call sites come with it, and the stack walk is off by default.
        executer.withArgument("-Dorg.gradle.internal.property-provenance=true")

        file("buildSrc/build.gradle") << """
            plugins { id 'java-library' }
            dependencies { implementation gradleApi() }
        """
        file("buildSrc/src/main/java/com/example/Show.java") << """
            package com.example;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.Property;
            import org.gradle.api.tasks.Input;

            public abstract class Show extends DefaultTask {
                // an input property is finalized before the task runs, so mutating it from a task
                // action is rejected, and that rejection is where provenance is reported
                @Input
                public abstract Property<String> getProp();
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/com.example.feature.properties") << """
            implementation-class=com.example.FeaturePlugin
        """
    }

    def "reports the call site of a mutation in instrumented build logic"() {
        featurePlugin('task.getProp().set("from plugin");')
        buildFile """
            plugins { id("com.example.feature") }

            tasks.named("show") {
                doLast { prop.set("other") }
            }
        """

        when:
        fails("show")

        then:
        failure.assertThatCause(matchesRegex(/(?s).*set by plugin 'com\.example\.feature' at FeaturePlugin\.java:\d+.*/))
    }

    def "reports the call site of a set from a provider"() {
        featurePlugin('task.getProp().set(project.provider(() -> "from plugin"));')
        buildFile """
            plugins { id("com.example.feature") }

            tasks.named("show") {
                doLast { prop.set("other") }
            }
        """

        when:
        fails("show")

        then:
        failure.assertThatCause(matchesRegex(/(?s).*set by plugin 'com\.example\.feature' at FeaturePlugin\.java:\d+.*/))
    }

    def "a Groovy build script assignment has no instrumented call site"() {
        featurePlugin('task.getProp().set("from plugin");')
        buildFile """
            plugins { id("com.example.feature") }

            tasks.named("show") {
                prop = "from build script"
                doLast { prop.set("other") }
            }
        """

        when:
        fails("show")

        then:
        // the contributor is still known; only the position is missing, because Groovy property
        // assignment goes through dynamic dispatch rather than a plain JVM call site
        failureCauseContains("It was configured by, in order:")
        failureCauseContains("set by build file 'build.gradle'")
        !failure.output.contains("at build.gradle:")
    }

    def "reports the call site of a set in a Kotlin DSL build script"() {
        featurePlugin('task.getProp().set("from plugin");')
        file("settings.gradle.kts") << ""
        file("build.gradle.kts") << '''
            plugins { id("com.example.feature") }

            tasks.named<com.example.Show>("show") {
                prop.set("from build script")
                doLast { prop.set("other") }
            }
        '''

        when:
        fails("show")

        then:
        failure.assertThatCause(matchesRegex(/(?s).*set by build file 'build\.gradle\.kts' at build\.gradle\.kts:\d+.*/))
    }

    private void featurePlugin(String mutation) {
        file("buildSrc/src/main/java/com/example/FeaturePlugin.java") << """
            package com.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class FeaturePlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getTasks().register("show", Show.class, task -> {
                        $mutation
                    });
                }
            }
        """
    }
}
