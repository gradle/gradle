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
package org.gradle.api.plugins.catalog;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.catalog.internal.CatalogExtensionInternal;
import org.gradle.api.plugins.catalog.internal.DefaultVersionCatalogPluginExtension;
import org.gradle.api.plugins.catalog.internal.TomlFileGenerator;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;

/**
 * <p>A {@link Plugin} makes it possible to generate a version catalog,  which is a set of versions and
 * coordinates for dependencies and plugins to import in the settings of a Gradle build.</p>
 *
 * @since 7.0
 */
public abstract class VersionCatalogPlugin implements Plugin<Project> {
    public static final String GENERATE_CATALOG_FILE_TASKNAME = "generateCatalogAsToml";
    public static final String GRADLE_PLATFORM_DEPENDENCIES = "versionCatalog";
    public static final String VERSION_CATALOG_ELEMENTS = "versionCatalogElements";

    private final SoftwareComponentFactory softwareComponentFactory;

    @Inject
    public VersionCatalogPlugin(SoftwareComponentFactory softwareComponentFactory) {
        this.softwareComponentFactory = softwareComponentFactory;
    }

    @Override
    public void apply(Project project) {
        Configuration dependenciesConfiguration = createDependenciesConfiguration((ProjectInternal) project);
        CatalogExtensionInternal extension = createExtension(project, dependenciesConfiguration);
        TaskProvider<TomlFileGenerator> generator = createGenerator(project, extension);
        createPublication((ProjectInternal) project, generator);
    }

    private void createPublication(ProjectInternal project, TaskProvider<TomlFileGenerator> generator) {
        Configuration exported = project.getConfigurations().migratingUnlocked(VERSION_CATALOG_ELEMENTS, ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE, cnf -> {
            cnf.setDescription("Artifacts for the version catalog");
            cnf.getOutgoing().artifact(generator);
            cnf.setVisible(false);
            cnf.attributes(attrs -> {
                attrs.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.REGULAR_PLATFORM));
                attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.VERSION_CATALOG));
            });
        });

        project.getPlugins().withType(BasePlugin.class, plugin -> {
            project.getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME).configure(assemble -> {
                assemble.dependsOn(exported.getArtifacts());
            });
        });

        AdhocComponentWithVariants versionCatalog = softwareComponentFactory.adhoc("versionCatalog");
        project.getComponents().add(versionCatalog);
        versionCatalog.addVariantsFromConfiguration(exported, new JavaConfigurationVariantMapping("compile", true, null));
    }

    private Configuration createDependenciesConfiguration(ProjectInternal project) {
        return project.getConfigurations().dependencyScopeUnlocked(GRADLE_PLATFORM_DEPENDENCIES, cnf -> {
            cnf.setVisible(false);
        });
    }

    private TaskProvider<TomlFileGenerator> createGenerator(Project project, CatalogExtensionInternal extension) {
        return project.getTasks().register(GENERATE_CATALOG_FILE_TASKNAME, TomlFileGenerator.class, t -> configureTask(project, extension, t));
    }

    private void configureTask(Project project, CatalogExtensionInternal extension, TomlFileGenerator task) {
        task.setGroup(BasePlugin.BUILD_GROUP);
        task.setDescription("Generates a TOML file for a version catalog");
        task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("version-catalog/libs.versions.toml"));
        task.getDependenciesModel().convention(extension.getVersionCatalog());
    }

    private CatalogExtensionInternal createExtension(Project project, Configuration dependenciesConfiguration) {
        return (CatalogExtensionInternal) project.getExtensions()
            .create(CatalogPluginExtension.class, "catalog", DefaultVersionCatalogPluginExtension.class, dependenciesConfiguration);
    }

}
