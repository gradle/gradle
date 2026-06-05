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

package org.gradle.features

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.features.internal.TestScenarioFixture
import org.gradle.test.fixtures.plugin.PluginBuilder

import static org.hamcrest.CoreMatchers.containsString

/**
 * Exercises the {@code SchemaDefinition} / {@code SchemaProjectTypeApplyAction} /
 * {@code SchemaProjectFeatureApplyAction} API end-to-end through the real feature-application
 * pipeline, registered via an {@code EcosystemApplyAction} (research Option 6): the ecosystem
 * aggregates the schema apply actions via {@code @RegistersProjectFeatures} and seeds defaults via
 * {@code apply(SharedModelDefaults)} — no {@code Plugin<Settings>} registrar, no {@code Plugin<Project>}
 * shell, and no {@code Binding}/{@code bind()} class. (The direct settings-application path is covered
 * by {@code SchemaDirectApplicationIntegrationTest}.)
 *
 * <p>Each apply action registers a task that prints a marker literal emitted only
 * from inside the simplified {@code apply(...)} method, so a passing assertion proves
 * the default {@code apply(...)} delegated to the simplified one through the pipeline.</p>
 */
class SchemaDefinitionIntegrationTest extends AbstractIntegrationSpec implements TestScenarioFixture {

    def 'project type with a SchemaDefinition runs its schema apply action'() {
        given:
        schemaPlugins()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("build.gradle.dcl") << """
            myProjectType {
                id = "abc"
            }
        """

        when:
        run("printMyProjectType")

        then:
        outputContains("SCHEMA_TYPE_APPLY_RAN id=abc")
    }

    def 'project feature with a SchemaDefinition runs its schema apply action with the parent'() {
        given:
        schemaPlugins()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("build.gradle.dcl") << """
            myProjectType {
                id = "abc"

                myFeature {
                    value = "xyz"
                }
            }
        """

        when:
        run("printMyFeature")

        then:
        outputContains("SCHEMA_FEATURE_APPLY_RAN value=xyz parentId=abc")
    }

    def 'ecosystem-seeded default applies when the build does not set it'() {
        given:
        schemaPlugins()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("build.gradle.dcl") << """
            myProjectType {
            }
        """

        when:
        run("printMyProjectType")

        then:
        outputContains("SCHEMA_TYPE_APPLY_RAN id=default-id")
    }

    def 'applying an ecosystem to a project fails with a clear settings-only error'() {
        given:
        schemaPlugins()

        settingsFile << """
            pluginManagement {
                includeBuild("plugins")
            }
        """

        buildFile << """
            plugins {
                id("com.example.test-software-ecosystem")
            }
        """

        when:
        fails("help")

        then:
        failure.assertThatCause(containsString("can only be applied in a settings 'plugins { }' block"))
    }

    /**
     * Writes an included "plugins" build containing a no-build-model project type and a
     * no-build-model project feature, authored as schema apply actions (no {@code Plugin}/{@code Binding}
     * shells), plus an {@code EcosystemApplyAction} that aggregates them via {@code @RegistersProjectFeatures}
     * and seeds a default {@code id} via {@code apply(SharedModelDefaults)}, exposed under the
     * {@code com.example.test-software-ecosystem} plugin id.
     */
    private void schemaPlugins() {
        PluginBuilder pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.packageName = "org.gradle.test"

        pluginBuilder.java("MyProjectTypeDefinition.java") << '''
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.features.binding.SchemaDefinition;

            public interface MyProjectTypeDefinition extends SchemaDefinition {
                Property<String> getId();
            }
        '''

        pluginBuilder.java("MyProjectTypeAction.java") << '''
            package org.gradle.test;

            import org.gradle.api.tasks.TaskContainer;
            import org.gradle.features.annotations.ProjectType;
            import org.gradle.features.binding.SchemaProjectTypeApplyAction;

            import javax.inject.Inject;

            @ProjectType(name = "myProjectType")
            public abstract class MyProjectTypeAction implements SchemaProjectTypeApplyAction<MyProjectTypeDefinition> {
                @Inject
                public MyProjectTypeAction() { }

                @Inject
                protected abstract TaskContainer getTasks();

                @Override
                public void apply(MyProjectTypeDefinition definition) {
                    getTasks().register("printMyProjectType", task -> {
                        String id = definition.getId().get();
                        task.doLast(t -> System.out.println("SCHEMA_TYPE_APPLY_RAN id=" + id));
                    });
                }
            }
        '''

        pluginBuilder.java("MyFeatureDefinition.java") << '''
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.features.binding.SchemaDefinition;

            public interface MyFeatureDefinition extends SchemaDefinition {
                Property<String> getValue();
            }
        '''

        pluginBuilder.java("MyFeatureAction.java") << '''
            package org.gradle.test;

            import org.gradle.api.tasks.TaskContainer;
            import org.gradle.features.annotations.ProjectFeature;
            import org.gradle.features.binding.SchemaProjectFeatureApplyAction;

            import javax.inject.Inject;

            @ProjectFeature(name = "myFeature")
            public abstract class MyFeatureAction implements SchemaProjectFeatureApplyAction<MyFeatureDefinition, MyProjectTypeDefinition> {
                @Inject
                public MyFeatureAction() { }

                @Inject
                protected abstract TaskContainer getTasks();

                @Override
                public void apply(MyFeatureDefinition definition, MyProjectTypeDefinition parent) {
                    getTasks().register("printMyFeature", task -> {
                        String value = definition.getValue().get();
                        String parentId = parent.getId().get();
                        task.doLast(t -> System.out.println("SCHEMA_FEATURE_APPLY_RAN value=" + value + " parentId=" + parentId));
                    });
                }
            }
        '''

        pluginBuilder.java("MyEcosystem.java") << '''
            package org.gradle.test;

            import org.gradle.api.initialization.EcosystemApplyAction;
            import org.gradle.api.initialization.SharedModelDefaults;
            import org.gradle.features.annotations.RegistersProjectFeatures;

            import javax.inject.Inject;

            @RegistersProjectFeatures({ MyProjectTypeAction.class, MyFeatureAction.class })
            public class MyEcosystem implements EcosystemApplyAction {
                @Inject
                public MyEcosystem() { }

                @Override
                public void apply(SharedModelDefaults defaults) {
                    defaults.add("myProjectType", MyProjectTypeDefinition.class,
                        definition -> definition.getId().convention("default-id"));
                }
            }
        '''

        pluginBuilder.addPluginId("com.example.test-software-ecosystem", "MyEcosystem")
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()
    }
}
