/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.docs;

import dev.adamko.dokkatoo.DokkatooExtension;
import dev.adamko.dokkatoo.dokka.parameters.DokkaSourceSetSpec;
import dev.adamko.dokkatoo.dokka.plugins.DokkaHtmlPluginParameters;
import dev.adamko.dokkatoo.formats.DokkatooHtmlPlugin;
import dev.adamko.dokkatoo.tasks.DokkatooGenerateTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.dokka.Platform;

public class GradleKotlinDslReferencePlugin implements Plugin<Project> {

    private static final String TASK_NAME = "dokkatooGeneratePublicationHtml";

    @Override
    public void apply(Project project) {
        GradleDocumentationExtension documentationExtension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        applyPlugin(project);
        updateDocumentationExtension(project, documentationExtension);
        configurePlugin(project, documentationExtension);
    }

    private static void applyPlugin(Project project) {
        project.getPlugins().apply(DokkatooHtmlPlugin.class);
    }

    private static void updateDocumentationExtension(Project project, GradleDocumentationExtension extension) {
        TaskProvider<Task> generateTask = project.getTasks().named(TASK_NAME);
        Provider<? extends Directory> outputDirectory = generateTask.flatMap(t -> ((DokkatooGenerateTask) t).getOutputDirectory());
        extension.getKotlinDslReference().getRenderedDocumentation().set(outputDirectory);
    }

    private static void configurePlugin(Project project, GradleDocumentationExtension extension) {
        renameModule(project);
        wireInArtificialSourceSet(project, extension);
        setStyling(project, extension);
    }

    /**
     * The name of the module is part of the URI for deep links, changing it will break existing links.
     * The name of the module must match the first header of {@code kotlin/Module.md} file.
     */
    private static void renameModule(Project project) {
        getDokkatooExtension(project).getModuleName().set("gradle");
    }

    private static void wireInArtificialSourceSet(Project project, GradleDocumentationExtension extension) {
        TaskProvider<GradleKotlinDslRuntimeGeneratedSources> runtimeExtensions = project.getTasks()
            .register("gradleKotlinDslRuntimeGeneratedSources", GradleKotlinDslRuntimeGeneratedSources.class, task -> {
                task.getGeneratedSources().set(project.getLayout().getBuildDirectory().dir("gradle-kotlin-dsl-extensions/sources"));
                task.getGeneratedClasses().set(project.getLayout().getBuildDirectory().dir("gradle-kotlin-dsl-extensions/classes"));
            });

        NamedDomainObjectContainer<DokkaSourceSetSpec> sourceSets = getDokkatooExtension(project).getDokkatooSourceSets();
        sourceSets.register("kotlin_dsl", spec -> {
            spec.getDisplayName().set("Kotlin DSL");
            spec.getSourceRoots().from(extension.getKotlinDslSource());
            spec.getSourceRoots().from(runtimeExtensions.flatMap(GradleKotlinDslRuntimeGeneratedSources::getGeneratedSources));
            spec.getClasspath().from(extension.getClasspath());
            spec.getClasspath().from(runtimeExtensions.flatMap(GradleKotlinDslRuntimeGeneratedSources::getGeneratedClasses));
            spec.getAnalysisPlatform().set(Platform.jvm);
            spec.getIncludes().from(extension.getSourceRoot().file("kotlin/Module.md"));
        });
    }

    private static void setStyling(Project project, GradleDocumentationExtension extension) {
        getDokkatooExtension(project).getPluginsConfiguration().named("html", DokkaHtmlPluginParameters.class, config -> {
            config.getCustomStyleSheets().from(extension.getSourceRoot().file("kotlin/styles/gradle.css"));
            config.getCustomAssets().from(extension.getSourceRoot().file("kotlin/images/gradle-logo.svg"));
            config.getFooterMessage().set("Gradle Kotlin DSL Reference");
        });
    }

    private static DokkatooExtension getDokkatooExtension(Project project) {
        return project.getExtensions().getByType(DokkatooExtension.class);
    }

}
