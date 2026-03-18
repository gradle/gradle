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
import gradlebuild.GeneratePackageInfoDataTask
import gradlebuild.GeneratePlatformsDataTask
import gradlebuild.GeneratorTask
import gradlebuild.PlatformBuilder
import gradlebuild.ProjectStructure
import gradlebuild.basics.ArchitectureDataType

val structure = extensions.create<ProjectStructure>("projectStructure")

gradle.rootProject {
    tasks.register("architectureDoc", GeneratorTask::class.java) {
        description = "Generates the architecture documentation"
        outputFile = layout.projectDirectory.file("architecture/platforms.md")
        elements = provider { structure.architectureElements.map { it.build() } }
    }
    val platformsData = tasks.register("platformsData", GeneratePlatformsDataTask::class) {
        description = "Generates the platforms data"
        outputFile = layout.buildDirectory.file("architecture/platforms.json")
        platforms = provider { structure.architectureElements.filterIsInstance<PlatformBuilder>().map { it.build() } }
    }
    val packageInfoData = tasks.register("packageInfoData", GeneratePackageInfoDataTask::class) {
        description = "Map packages to the list of package-info.java files that apply to them"
        outputFile = layout.buildDirectory.file("architecture/package-info.json")
        packageInfoFiles.from(GeneratePackageInfoDataTask.findPackageInfoFiles(objects, provider { structure.projectBaseDirs }))
    }

    configurations.consumable("platformsData") {
        outgoing.artifact(platformsData)
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(ArchitectureDataType.PLATFORMS))
        }
    }

    configurations.consumable("packageInfoData") {
        outgoing.artifact(packageInfoData)
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(ArchitectureDataType.PACKAGE_INFO))
        }
    }
}
