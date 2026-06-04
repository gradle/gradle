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

/**
 * Exercises the {@code SchemaDefinition} / {@code SchemaProjectTypeApplyAction} /
 * {@code SchemaProjectFeatureApplyAction} API end-to-end through the real
 * feature-application pipeline, using a hand-authored plugin (the shared
 * {@code testScenario} fixtures still emit the non-schema apply-action types).
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

    /**
     * Writes an included "plugins" build containing a no-build-model project type and a
     * no-build-model project feature bound to it, plus a settings plugin that registers
     * them, exposed under the {@code com.example.test-software-ecosystem} plugin id.
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

        pluginBuilder.java("MyProjectTypePlugin.java") << '''
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.features.annotations.BindsProjectType;
            import org.gradle.features.binding.ProjectTypeBinding;
            import org.gradle.features.binding.ProjectTypeBindingBuilder;
            import org.gradle.features.binding.SchemaProjectTypeApplyAction;
            import org.gradle.features.registration.TaskRegistrar;

            import javax.inject.Inject;

            @BindsProjectType(MyProjectTypePlugin.Binding.class)
            public abstract class MyProjectTypePlugin implements Plugin<Project> {

                static class Binding implements ProjectTypeBinding {
                    @Override
                    public void bind(ProjectTypeBindingBuilder builder) {
                        builder.bindProjectType("myProjectType", MyProjectTypeDefinition.class, ApplyAction.class);
                    }
                }

                static abstract class ApplyAction implements SchemaProjectTypeApplyAction<MyProjectTypeDefinition> {
                    @Inject
                    public ApplyAction() { }

                    @Inject
                    protected abstract TaskRegistrar getTaskRegistrar();

                    @Override
                    public void apply(MyProjectTypeDefinition definition) {
                        getTaskRegistrar().register("printMyProjectType", task -> {
                            String id = definition.getId().get();
                            task.doLast(t -> System.out.println("SCHEMA_TYPE_APPLY_RAN id=" + id));
                        });
                    }
                }

                @Override
                public void apply(Project project) {
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

        pluginBuilder.java("MyFeaturePlugin.java") << '''
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.features.annotations.BindsProjectFeature;
            import org.gradle.features.binding.ProjectFeatureBinding;
            import org.gradle.features.binding.ProjectFeatureBindingBuilder;
            import org.gradle.features.binding.SchemaProjectFeatureApplyAction;
            import org.gradle.features.registration.TaskRegistrar;

            import javax.inject.Inject;

            @BindsProjectFeature(MyFeaturePlugin.Binding.class)
            public abstract class MyFeaturePlugin implements Plugin<Project> {

                static class Binding implements ProjectFeatureBinding {
                    @Override
                    public void bind(ProjectFeatureBindingBuilder builder) {
                        builder.bindProjectFeatureToDefinition("myFeature", MyFeatureDefinition.class, MyProjectTypeDefinition.class, ApplyAction.class);
                    }
                }

                static abstract class ApplyAction implements SchemaProjectFeatureApplyAction<MyFeatureDefinition, MyProjectTypeDefinition> {
                    @Inject
                    public ApplyAction() { }

                    @Inject
                    protected abstract TaskRegistrar getTaskRegistrar();

                    @Override
                    public void apply(MyFeatureDefinition definition, MyProjectTypeDefinition parent) {
                        getTaskRegistrar().register("printMyFeature", task -> {
                            String value = definition.getValue().get();
                            String parentId = parent.getId().get();
                            task.doLast(t -> System.out.println("SCHEMA_FEATURE_APPLY_RAN value=" + value + " parentId=" + parentId));
                        });
                    }
                }

                @Override
                public void apply(Project project) {
                }
            }
        '''

        pluginBuilder.java("ProjectTypeRegistrationPlugin.java") << '''
            package org.gradle.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.features.annotations.RegistersProjectFeatures;

            @RegistersProjectFeatures({ MyProjectTypePlugin.class, MyFeaturePlugin.class })
            public abstract class ProjectTypeRegistrationPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings settings) {
                }
            }
        '''

        pluginBuilder.addPluginId("com.example.test-software-ecosystem", "ProjectTypeRegistrationPlugin")
        pluginBuilder.addBuildScriptContent(pluginBuildScriptForJava)
        pluginBuilder.prepareToExecute()
    }
}
