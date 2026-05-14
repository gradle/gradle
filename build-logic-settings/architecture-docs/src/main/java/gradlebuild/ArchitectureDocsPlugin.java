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

package gradlebuild;

import gradlebuild.basics.ArchitectureDataType;
import org.gradle.api.Plugin;
import org.gradle.api.attributes.Category;
import org.gradle.api.initialization.Settings;
import org.gradle.api.tasks.TaskProvider;

import java.util.stream.Collectors;

public class ArchitectureDocsPlugin implements Plugin<Settings> {

    @Override
    public void apply(Settings settings) {
        GradleArchitecture architecture = settings.getExtensions().create("architecture", GradleArchitecture.class, settings);

        settings.getGradle().rootProject(rootProject -> {
            rootProject.getTasks().register("architectureDoc", GeneratorTask.class, task -> {
                task.setDescription("Generates the architecture documentation");
                task.getOutputFile().set(rootProject.getLayout().getProjectDirectory().file("architecture/platforms.md"));
                task.getElements().set(rootProject.provider(() ->
                    architecture.getArchitectureElements().stream()
                        .map(ArchitectureElementBuilder::build)
                        .collect(Collectors.toList())));
            });

            TaskProvider<GeneratePlatformsDataTask> platformsData = rootProject.getTasks().register("platformsData", GeneratePlatformsDataTask.class, task -> {
                task.setDescription("Generates the platforms data");
                task.getOutputFile().set(rootProject.getLayout().getBuildDirectory().file("architecture/platforms.json"));
                task.getPlatforms().set(rootProject.provider(() ->
                    architecture.getArchitectureElements().stream()
                        .filter(PlatformBuilder.class::isInstance)
                        .map(PlatformBuilder.class::cast)
                        .map(PlatformBuilder::build)
                        .collect(Collectors.toList())));
            });

            TaskProvider<GeneratePackageInfoDataTask> packageInfoData = rootProject.getTasks().register("packageInfoData", GeneratePackageInfoDataTask.class, task -> {
                task.setDescription("Map packages to the list of package-info.java files that apply to them");
                task.getOutputFile().set(rootProject.getLayout().getBuildDirectory().file("architecture/package-info.json"));
                task.getPackageInfoFiles().from(GeneratePackageInfoDataTask.findPackageInfoFiles(
                    rootProject.getObjects(),
                    rootProject.provider(architecture::getProjectBaseDirs)));
            });

            rootProject.getConfigurations().consumable("platformsData", configuration -> {
                configuration.getOutgoing().artifact(platformsData);
                configuration.attributes(attributes ->
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE,
                        rootProject.getObjects().named(Category.class, ArchitectureDataType.PLATFORMS)));
            });

            rootProject.getConfigurations().consumable("packageInfoData", configuration -> {
                configuration.getOutgoing().artifact(packageInfoData);
                configuration.attributes(attributes ->
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE,
                        rootProject.getObjects().named(Category.class, ArchitectureDataType.PACKAGE_INFO)));
            });
        });
    }
}
