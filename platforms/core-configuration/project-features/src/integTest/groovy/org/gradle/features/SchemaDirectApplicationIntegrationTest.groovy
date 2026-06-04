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
 * Exercises the low-ceremony "direct application" path: a plugin id whose descriptor points directly
 * at a {@code SchemaProjectTypeApplyAction}/{@code SchemaProjectFeatureApplyAction} (annotated with
 * {@code @ProjectType}/{@code @ProjectFeature}) is applied in a settings {@code plugins { }} block,
 * registering the project type/feature with no project plugin shell, binding class, or settings plugin.
 *
 * <p>This is the low-ceremony twin of {@link SchemaDefinitionIntegrationTest}.</p>
 */
class SchemaDirectApplicationIntegrationTest extends AbstractIntegrationSpec implements TestScenarioFixture {

    def 'schema project type and feature applied directly in settings run their apply actions'() {
        given:
        schemaPlugins()

        file("settings.gradle.dcl") << """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.my-project-type")
                id("com.example.my-feature")
            }
        """

        file("build.gradle.dcl") << """
            myProjectType {
                id = "abc"

                myFeature {
                    value = "xyz"
                }
            }
        """

        when:
        run("printMyProjectType", "printMyFeature")

        then:
        outputContains("SCHEMA_TYPE_APPLY_RAN id=abc")
        outputContains("SCHEMA_FEATURE_APPLY_RAN value=xyz parentId=abc")
    }

    def 'applying a schema apply action to a project fails with a clear settings-only error'() {
        given:
        schemaPlugins()

        settingsFile << """
            pluginManagement {
                includeBuild("plugins")
            }
        """

        buildFile << """
            plugins {
                id("com.example.my-project-type")
            }
        """

        when:
        fails("help")

        then:
        failure.assertThatCause(containsString("can only be applied in a settings 'plugins { }' block"))
    }

    /**
     * Writes an included "plugins" build whose plugin descriptors point directly at schema apply
     * action classes — there is no project plugin, binding class, or settings plugin.
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

            import org.gradle.features.annotations.ProjectType;
            import org.gradle.features.binding.SchemaProjectTypeApplyAction;
            import org.gradle.api.tasks.TaskContainer;

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

            import org.gradle.features.annotations.ProjectFeature;
            import org.gradle.features.binding.SchemaProjectFeatureApplyAction;
            import org.gradle.api.tasks.TaskContainer;

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

        pluginBuilder.addPluginId("com.example.my-project-type", "MyProjectTypeAction")
        pluginBuilder.addPluginId("com.example.my-feature", "MyFeatureAction")
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()
    }
}
