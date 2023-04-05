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
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.dokka.Platform;

public class GradleKotlinDslReferencePlugin implements Plugin<Project> {

    public static final String TASK_NAME = "dokkatooGeneratePublicationHtml";
    public static final String MODULE_NAME = "Kotlin DSL Reference";

    @Override
    public void apply(Project project) {
        GradleDocumentationExtension documentationExtension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        applyPlugin(project);
        updateDocumentationExtension(project, documentationExtension);
        configurePlugin(project, documentationExtension);
    }

    private void applyPlugin(Project project) {
        project.getPlugins().apply(DokkatooHtmlPlugin.class);
    }

    private void updateDocumentationExtension(Project project, GradleDocumentationExtension extension) {
        DokkatooGenerateTask generateTask = (DokkatooGenerateTask) project.getTasks().getByName(TASK_NAME);
        extension.getKotlinDslReference().getRenderedDocumentation().from(generateTask.getOutputDirectory());

        extension.getKotlinDslReference().getDokkaCss().convention(extension.getSourceRoot().file("css/dokka.css"));
    }

    private void configurePlugin(Project project, GradleDocumentationExtension extension) {
        renameModule(project);
        wireInArtificialSourceSet(project, extension);
        setStyling(project, extension);
    }

    private static DokkatooExtension renameModule(Project project) {
        DokkatooExtension dokkatooExtension = getDokkatooExtension(project);
        dokkatooExtension.getModuleName().set(MODULE_NAME);
        return dokkatooExtension;
    }

    private static void wireInArtificialSourceSet(Project project, GradleDocumentationExtension extension) {
        TaskProvider<GradleKotlinDslRuntimeGeneratedSources> runtimeExtensions = project.getTasks()
            .register("gradleKotlinDslRuntimeGeneratedSources", GradleKotlinDslRuntimeGeneratedSources.class, task -> {
                task.getGeneratedSources().set(project.getLayout().getBuildDirectory().dir("gradle-kotlin-dsl-extensions/sources"));
                task.getGeneratedClasses().set(project.getLayout().getBuildDirectory().dir("gradle-kotlin-dsl-extensions/classes"));
            });

        NamedDomainObjectContainer<DokkaSourceSetSpec> sourceSets = getDokkatooExtension(project).getDokkatooSourceSets();
        sourceSets.register("kotlin_dsl", spec -> {
            spec.getDisplayName().set(MODULE_NAME);
            spec.getSourceRoots().from(extension.getKotlinDslSource());
            spec.getSourceRoots().from(runtimeExtensions.flatMap(GradleKotlinDslRuntimeGeneratedSources::getGeneratedSources));
            spec.getClasspath().from(extension.getClasspath());
            spec.getClasspath().from(runtimeExtensions.flatMap(GradleKotlinDslRuntimeGeneratedSources::getGeneratedClasses));
            spec.getAnalysisPlatform().set(Platform.jvm);
            spec.getIncludes().from(extension.getSourceRoot().file("kotlin/Module.md").get().getAsFile().getAbsolutePath());
        });
    }

    private static void setStyling(Project project, GradleDocumentationExtension extension) {
        String cssFile = extension.getSourceRoot().file("kotlin/styles/logo-styles.css").get().getAsFile().getAbsolutePath();
        String logoFile = extension.getSourceRoot().file("kotlin/images/gradle-logo.png").get().getAsFile().getAbsolutePath();

        getDokkatooExtension(project).getPluginsConfiguration().named("html", DokkaHtmlPluginParameters.class, config -> {
            config.getCustomStyleSheets().from(cssFile);
            config.getCustomAssets().from(logoFile);
        });
    }

    private static DokkatooExtension getDokkatooExtension(Project project) {
        return project.getExtensions().getByType(DokkatooExtension.class);
    }

}
