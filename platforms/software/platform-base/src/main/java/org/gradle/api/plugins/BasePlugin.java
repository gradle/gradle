/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.plugins.BuildConfigurationRule;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.plugins.NaggingBasePluginConvention;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.ChangingValue;
import org.gradle.api.internal.provider.ChangingValueHandler;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.plugins.internal.DefaultBasePluginExtension;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * <p>A {@link org.gradle.api.Plugin} which defines a basic project lifecycle and some common convention properties.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html">Base plugin reference</a>
 */
public abstract class BasePlugin implements Plugin<Project> {
    public static final String CLEAN_TASK_NAME = LifecycleBasePlugin.CLEAN_TASK_NAME;
    public static final String ASSEMBLE_TASK_NAME = LifecycleBasePlugin.ASSEMBLE_TASK_NAME;
    public static final String BUILD_GROUP = LifecycleBasePlugin.BUILD_GROUP;

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        BasePluginExtension baseExtension = project.getExtensions().create(BasePluginExtension.class, "base", DefaultBasePluginExtension.class, project);

        addConvention(project, baseExtension);
        configureExtension(project, baseExtension);
        configureBuildConfigurationRule(project);
        configureArchiveDefaults(project, baseExtension);
        configureConfigurations(project);
    }


    @SuppressWarnings("deprecation")
    private static void addConvention(Project project, BasePluginExtension baseExtension) {
        BasePluginConvention convention = project.getObjects().newInstance(org.gradle.api.plugins.internal.DefaultBasePluginConvention.class, baseExtension);
        DeprecationLogger.whileDisabled(() -> {
            project.getConvention().getPlugins().put("base", new NaggingBasePluginConvention(convention));
        });
    }

    private static void configureExtension(Project project, BasePluginExtension extension) {
        extension.getArchivesName().convention(project.getName());
        extension.getLibsDirectory().convention(project.getLayout().getBuildDirectory().dir("libs"));
        extension.getDistsDirectory().convention(project.getLayout().getBuildDirectory().dir("distributions"));
    }

    private static void configureArchiveDefaults(final Project project, final BasePluginExtension extension) {
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(task -> {
            task.getDestinationDirectory().convention(extension.getDistsDirectory());
            task.getArchiveVersion().convention(
                project.provider(() -> project.getVersion() == Project.DEFAULT_VERSION ? null : project.getVersion().toString())
            );

            task.getArchiveBaseName().convention(extension.getArchivesName());
        });
    }

    private static void configureBuildConfigurationRule(Project project) {
        project.getTasks().addRule(new BuildConfigurationRule(project.getConfigurations(), project.getTasks()));
    }

    private static void configureConfigurations(final Project project) {
        RoleBasedConfigurationContainerInternal configurations = (RoleBasedConfigurationContainerInternal) project.getConfigurations();
        ((ProjectInternal) project).getInternalStatus().convention("integration");

        final Configuration archivesConfiguration = configurations.maybeCreateMigratingUnlocked(Dependency.ARCHIVES_CONFIGURATION, ConfigurationRolesForMigration.CONSUMABLE_TO_REMOVED)
            .setDescription("Configuration for archive artifacts.");

        configurations.maybeCreateConsumableUnlocked(Dependency.DEFAULT_CONFIGURATION)
            .setDescription("Configuration for default artifacts.");

        PublishArtifactSet archivesArtifacts = DeprecationLogger.whileDisabled(archivesConfiguration::getArtifacts);

        // This extension is deprecated, adding artifacts to it directly adds artifacts to the archives configuration.
        // Even though it is deprecated, Kotlin still uses it.
        // See https://github.com/JetBrains/kotlin/blob/54da79fbc4034054c724b6be89cf6f4aca225fe5/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/configureBinaryFrameworks.kt#L91-L100
        project.getExtensions().create("defaultArtifacts", DefaultArtifactPublicationSet.class, archivesArtifacts);

        // We still need to automatically add artifacts to the archives configuration, as nagging would
        // require us telling users to call setVisible(false) on all consumable configurations.
        // However, we plan to get rid of setVisible, so we don't want users littering that call everywhere
        // Instead, we will change this behavior of auto-building artifacts in 9.0 and just notify about
        // the change in behavior in the upgrade guide.
        // TODO: When removing this code be sure to add an entry to the upgrade guide as a potential breaking change.
        archivesArtifacts.addAllLater(new DefaultArtifactProvider(configurations));

        project.getTasks().named(ASSEMBLE_TASK_NAME, task -> {
            task.dependsOn(DeprecationLogger.whileDisabled(archivesConfiguration::getAllArtifacts));
        });
    }

    /**
     * A live provider of all artifacts not in the archives configuration.
     * <p>
     * This is needed to exercise behavior in the container infrastructure that invalidates caches
     * after the artifact set changes. This allows the set of provided artifacts to change even
     * after the value has been calculated. When the set of artifacts changes, any containers
     * that include this provider will invalidate its cached values for it.
     * <p>
     * This is particularly important since {@link Configuration#getAllArtifacts()} eagerly resolves
     * artifact sets, meaning it is possible for build logic to call {@code getAllArtifacts} before all
     * configurations have their artifacts initialized.
     */
    private static class DefaultArtifactProvider extends AbstractMinimalProvider<Set<PublishArtifact>> implements CollectionProviderInternal<PublishArtifact, Set<PublishArtifact>>, ChangingValue<Set<PublishArtifact>> {

        private final ConfigurationContainer configurations;
        private final ChangingValueHandler<Set<PublishArtifact>> changingValue = new ChangingValueHandler<>();

        boolean subscribed = false;
        private ImmutableSet<PublishArtifact> lastArtifacts;
        private ImmutableSet<PublishArtifact> artifacts = ImmutableSet.of();

        public DefaultArtifactProvider(ConfigurationContainer configurations) {
            this.configurations = configurations;
        }

        @Override
        public Class<? extends PublishArtifact> getElementType() {
            return PublishArtifact.class;
        }

        @Override
        public int size() {
            return artifacts.size();
        }

        @Nullable
        @Override
        public Class<Set<PublishArtifact>> getType() {
            return null;
        }

        @Override
        protected Value<Set<PublishArtifact>> calculateOwnValue(ValueConsumer consumer) {
            maybeSubscribe();
            this.lastArtifacts = artifacts;
            return Value.of(artifacts);
        }

        private void maybeSubscribe() {
            if (!subscribed) {
                configurations.all(conf -> {
                    if (conf.isVisible() && !conf.getName().equals(Dependency.ARCHIVES_CONFIGURATION)) {
                        conf.getArtifacts().all(this::addArtifact);
                    }
                });
                subscribed = true;
            }
        }

        void addArtifact(PublishArtifact artifact) {
            if (!artifacts.contains(artifact)) {
                artifacts = ImmutableSet.<PublishArtifact>builder()
                    .addAll(artifacts)
                    .add(artifact)
                    .build();

                // If we changed the artifacts in the set, invalidate any consumers that have cached the prior value.
                if (lastArtifacts != null) {
                    ImmutableSet<PublishArtifact> previousArtifacts = lastArtifacts;
                    lastArtifacts = null;
                    changingValue.handle(previousArtifacts);
                }
            }
        }

        @Override
        public void onValueChange(Action<Set<PublishArtifact>> action) {
            changingValue.onValueChange(action);
        }
    }
}
