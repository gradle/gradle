/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.Sets;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencyConstraintSet;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.std.AllDependenciesModel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.internal.gradleplatform.DefaultGradlePlatformExtension;
import org.gradle.api.plugins.internal.gradleplatform.GradlePlatformExtensionInternal;
import org.gradle.api.plugins.internal.gradleplatform.TomlFileGenerator;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@link Plugin} makes it possible to generate a "Gradle platform", which is a set of recommendations
 * for dependency and plugin versions</p>
 *
 * @since 6.8
 */
@Incubating
public class GradlePlatformPlugin implements Plugin<Project> {
    private final static Logger LOGGER = Logging.getLogger(GradlePlatformPlugin.class);

    public static final String GENERATE_PLATFORM_FILE_TASKNAME = "generatePlatformToml";
    public static final String GRADLE_PLATFORM_DEPENDENCIES = "gradlePlatform";
    public static final String GRADLE_PLATFORM_ELEMENTS = "gradlePlatformElements";

    private final SoftwareComponentFactory softwareComponentFactory;

    @Inject
    public GradlePlatformPlugin(SoftwareComponentFactory softwareComponentFactory) {
        this.softwareComponentFactory = softwareComponentFactory;
    }

    @Override
    public void apply(Project project) {
        GradlePlatformExtensionInternal extension = createExtension(project);
        TaskProvider<TomlFileGenerator> generator = createGenerator(project, extension);
        createPublication(project, generator);
    }

    private void createPublication(Project project, TaskProvider<TomlFileGenerator> generator) {
        Configuration dependencies = project.getConfigurations().create(GRADLE_PLATFORM_DEPENDENCIES, cnf -> {
            cnf.setVisible(false);
            cnf.setCanBeConsumed(false);
            cnf.setCanBeResolved(false);
        });
        Configuration exported = project.getConfigurations().create(GRADLE_PLATFORM_ELEMENTS, cnf -> {
            cnf.setDescription("Artifacts for the Gradle platform");
            cnf.setCanBeConsumed(true);
            cnf.setCanBeResolved(false);
            cnf.getOutgoing().artifact(generator);
            cnf.attributes(attrs -> {
                attrs.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.REGULAR_PLATFORM));
                attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.GRADLE_RECOMMENDATIONS));
            });
        });
        AdhocComponentWithVariants gradlePlatform = softwareComponentFactory.adhoc("gradlePlatform");
        project.getComponents().add(gradlePlatform);
        gradlePlatform.addVariantsFromConfiguration(exported, new JavaConfigurationVariantMapping("compile", true));
    }

    private TaskProvider<TomlFileGenerator> createGenerator(Project project, GradlePlatformExtensionInternal extension) {
        return project.getTasks().register(GENERATE_PLATFORM_FILE_TASKNAME, TomlFileGenerator.class, t -> configureTask(project, extension, t));
    }

    private void configureTask(Project project, GradlePlatformExtensionInternal extension, TomlFileGenerator task) {
        task.setGroup(BasePlugin.BUILD_GROUP);
        task.setDescription("Generates a TOML file for a Gradle platform");
        task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("gradle-platform/dependencies.toml"));
        task.getDependenciesModel().convention(createDependenciesModel(project, extension));
        task.getPluginVersions().convention(extension.getPluginVersions());
    }

    private GradlePlatformExtensionInternal createExtension(Project project) {
        return (GradlePlatformExtensionInternal) project.getExtensions()
            .create(GradlePlatformExtension.class, "gradlePlatform", DefaultGradlePlatformExtension.class);
    }

    private Provider<AllDependenciesModel> createDependenciesModel(Project project, GradlePlatformExtensionInternal extension) {
        return project.getProviders().provider(() -> {
            Configuration dependencies = project.getConfigurations().getByName(GRADLE_PLATFORM_DEPENDENCIES);
            DependencySet allDependencies = dependencies.getAllDependencies();
            DependencyConstraintSet allDependencyConstraints = dependencies.getAllDependencyConstraints();
            if (allDependencies.isEmpty() && allDependencyConstraints.isEmpty()) {
                return extension.getDependenciesModel().get();
            }
            Set<ModuleIdentifier> seen = Sets.newHashSet();
            collectDependencies(extension, allDependencies, seen);
            collectConstraints(extension, allDependencyConstraints, seen);
            return extension.getDependenciesModel().get();
        });
    }

    private void collectDependencies(GradlePlatformExtensionInternal extension, DependencySet allDependencies, Set<ModuleIdentifier> seen) {
        extension.dependenciesModel(model -> {
            Map<ModuleIdentifier, String> explicitAliases = extension.getExplicitAliases();
            for (Dependency dependency : allDependencies) {
                String group = dependency.getGroup();
                String name = dependency.getName();
                if (group != null) {
                    ModuleIdentifier id = DefaultModuleIdentifier.newId(group, name);
                    if (seen.add(id)) {
                        String alias = explicitAliases.get(id);
                        if (alias != null) {
                            model.alias(alias, group, name, v -> copyDependencyVersion(dependency, group, name, v));
                        } else {
                            extension.tryGenericAlias(group, name, v -> copyDependencyVersion(dependency, group, name, v));
                        }
                    } else {
                        LOGGER.warn("Duplicate entry for dependency " + group + ":" + name);
                    }
                }
            }
        });
    }

    private static void copyDependencyVersion(Dependency dependency, String group, String name, MutableVersionConstraint v) {
        if (dependency instanceof ExternalModuleDependency) {
            VersionConstraint vc = ((ExternalModuleDependency) dependency).getVersionConstraint();
            copyConstraint(vc, v);
        } else {
            String version = dependency.getVersion();
            if (version == null || version.isEmpty()) {
                throw new InvalidUserDataException("Version for dependency " + group + ":" + name + " must not be empty");
            }
            v.require(version);
        }
    }

    private void collectConstraints(GradlePlatformExtensionInternal extension, DependencyConstraintSet allConstraints, Set<ModuleIdentifier> seen) {
        extension.dependenciesModel(model -> {
            Map<ModuleIdentifier, String> explicitAliases = extension.getExplicitAliases();
            for (DependencyConstraint constraint : allConstraints) {
                String group = constraint.getGroup();
                String name = constraint.getName();
                ModuleIdentifier id = DefaultModuleIdentifier.newId(group, name);
                if (seen.add(id)) {
                    String alias = explicitAliases.get(id);
                    if (alias != null) {
                        model.alias(alias, group, name, into -> copyConstraint(constraint.getVersionConstraint(), into));
                    } else {
                        extension.tryGenericAlias(group, name, into -> copyConstraint(constraint.getVersionConstraint(), into));
                    }
                } else {
                    LOGGER.warn("Duplicate entry for constraint " + group + ":" + name);
                }
            }
        });
    }

    private static void copyConstraint(VersionConstraint from, MutableVersionConstraint into) {
        if (!from.getRequiredVersion().isEmpty()) {
            into.require(from.getRequiredVersion());
        }
        if (!from.getStrictVersion().isEmpty()) {
            into.strictly(from.getStrictVersion());
        }
        if (!from.getPreferredVersion().isEmpty()) {
            into.prefer(from.getPreferredVersion());
        }
        if (!from.getRejectedVersions().isEmpty()) {
            into.reject(from.getRejectedVersions().toArray(new String[0]));
        }
    }
}
