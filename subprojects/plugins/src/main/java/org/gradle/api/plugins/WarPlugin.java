/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.java.WebApplication;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.DefaultWarPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.War;
import org.gradle.internal.deprecation.DeprecatableConfiguration;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to add tasks which assemble a web application into a WAR
 * file.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/war_plugin.html">WAR plugin reference</a>
 */
public class WarPlugin implements Plugin<Project> {
    public static final String PROVIDED_COMPILE_CONFIGURATION_NAME = "providedCompile";
    public static final String PROVIDED_RUNTIME_CONFIGURATION_NAME = "providedRuntime";
    public static final String WAR_TASK_NAME = "war";
    public static final String WEB_APP_GROUP = "web application";

    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;

    @Inject
    public WarPlugin(ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory) {
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        final WarPluginConvention pluginConvention = new DefaultWarPluginConvention(project);
        project.getConvention().getPlugins().put("war", pluginConvention);

        project.getTasks().withType(War.class).configureEach(task -> {
            task.getWebAppDirectory().convention(project.getLayout().dir(project.provider(() -> pluginConvention.getWebAppDir())));
            task.from(task.getWebAppDirectory());
            task.dependsOn((Callable) () -> project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                .getRuntimeClasspath());
            task.classpath((Callable) () -> {
                FileCollection runtimeClasspath = project.getExtensions().getByType(JavaPluginExtension.class)
                    .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath();
                Configuration providedRuntime = project.getConfigurations().getByName(PROVIDED_RUNTIME_CONFIGURATION_NAME);
                return runtimeClasspath.minus(providedRuntime);
            });
        });

        TaskProvider<War> war = project.getTasks().register(WAR_TASK_NAME, War.class, warTask -> {
            warTask.setDescription("Generates a war archive with all the compiled classes, the web-app content and the libraries.");
            warTask.setGroup(BasePlugin.BUILD_GROUP);
        });

        PublishArtifact warArtifact = new LazyPublishArtifact(war, ((ProjectInternal) project).getFileResolver());
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(warArtifact);
        configureConfigurations(project.getConfigurations());
        configureComponent(project, warArtifact);
    }

    public void configureConfigurations(ConfigurationContainer configurationContainer) {
        Configuration providedCompileConfiguration = configurationContainer.create(PROVIDED_COMPILE_CONFIGURATION_NAME).setVisible(false).
            setDescription("Additional compile classpath for libraries that should not be part of the WAR archive.");
        deprecateForConsumption(providedCompileConfiguration);

        Configuration providedRuntimeConfiguration = configurationContainer.create(PROVIDED_RUNTIME_CONFIGURATION_NAME).setVisible(false).
            extendsFrom(providedCompileConfiguration).
            setDescription("Additional runtime classpath for libraries that should not be part of the WAR archive.");
        deprecateForConsumption(providedRuntimeConfiguration);

        configurationContainer.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(providedCompileConfiguration);
        configurationContainer.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(providedRuntimeConfiguration);
        configurationContainer.getByName(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(providedRuntimeConfiguration);
        configurationContainer.getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME).extendsFrom(providedRuntimeConfiguration);
    }

    private static void deprecateForConsumption(Configuration configuration) {
        ((DeprecatableConfiguration) configuration).deprecateForConsumption(deprecation -> deprecation
            .willBecomeAnErrorInGradle8()
            .withUpgradeGuideSection(7, "plugin_configuration_consumption"));
    }

    private void configureComponent(Project project, PublishArtifact warArtifact) {
        AttributeContainer attributes = attributesFactory.mutable()
            .attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        project.getComponents().add(objectFactory.newInstance(WebApplication.class, warArtifact, "master", attributes));
    }
}
