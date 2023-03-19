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

import dev.adamko.dokkatoo.DokkatooBasePlugin;
import dev.adamko.dokkatoo.DokkatooExtension;
import dev.adamko.dokkatoo.dokka.parameters.DokkaSourceSetSpec;
import dev.adamko.dokkatoo.formats.DokkatooHtmlPlugin;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.dokka.DokkaConfiguration;
import org.jetbrains.dokka.Platform;

public class GradleKotlinDslReferencePlugin implements Plugin<Project> {

    public static final String TASK_NAME = "dokkatooGeneratePublicationHtml";

    @Override
    public void apply(Project project) {
        GradleDocumentationExtension documentationExtension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        applyAndConfigurePlugin(project, documentationExtension);
        updateExtension(project, documentationExtension);
        configurePublication(project, documentationExtension);
    }

    private void applyAndConfigurePlugin(Project project, GradleDocumentationExtension extension) {
        TaskProvider<GradleKotlinDslRuntimeGeneratedSources> runtimeExtensions = project.getTasks()
            .register("gradleKotlinDslRuntimeGeneratedSources", GradleKotlinDslRuntimeGeneratedSources.class, task -> {
                task.getGeneratedSources().set(project.getLayout().getBuildDirectory().dir("gradle-kotlin-dsl-extensions/sources"));
                task.getGeneratedClasses().set(project.getLayout().getBuildDirectory().dir("gradle-kotlin-dsl-extensions/classes"));
            });

        // apply base plugin for the extension to exist
        project.getPlugins().apply(DokkatooBasePlugin.class);

        // wire in our artificial sourceset into the extension
        NamedDomainObjectContainer<DokkaSourceSetSpec> sourceSets = getDokkatooExtension(project).getDokkatooSourceSets();
        sourceSets.register("kotlin_dsl", new Action<>() {
            @Override
            public void execute(DokkaSourceSetSpec spec) {
                spec.getDisplayName().set("Gradle Kotlin DSL");
                spec.getSourceRoots().from(extension.getKotlinDslSource());
                spec.getSourceRoots().from(runtimeExtensions.flatMap(GradleKotlinDslRuntimeGeneratedSources::getGeneratedSources));
                spec.getClasspath().from(extension.getClasspath());
                spec.getClasspath().from(runtimeExtensions.flatMap(GradleKotlinDslRuntimeGeneratedSources::getGeneratedClasses));
                spec.getAnalysisPlatform().set(Platform.jvm);
            }
        });

        // apply formatting plugin
        project.getPlugins().apply(DokkatooHtmlPlugin.class);
    }

    private void updateExtension(Project project, GradleDocumentationExtension extension) {
        DirectoryProperty publicationDirectory = getDokkatooExtension(project).getDokkatooPublicationDirectory();
        extension.getKotlinDslReference().getRenderedDocumentation().from(publicationDirectory);
        //TODO: publication directory should come from task output instead, but we have DokkaTasks that depend on DokkatooGenerateTasks and haven't found a way to obtain the output

        extension.getKotlinDslReference().getDokkaCss().convention(extension.getSourceRoot().file("css/dokka.css"));
    }

    private void configurePublication(Project project, GradleDocumentationExtension extension) {
        String cssFile = extension.getKotlinDslReference().getDokkaCss().get().getAsFile().getAbsolutePath();
        String logoFile = extension.getSourceRoot().file("images/gradle-logo.png").get().getAsFile().getAbsolutePath();

        getDokkatooExtension(project).getDokkatooPublications().configureEach(publication -> {
            publication.getPluginsConfiguration().create("org.jetbrains.dokka.base.DokkaBase", config -> {
                config.getSerializationFormat().set(DokkaConfiguration.SerializationFormat.JSON);
                config.getValues().set("" +
                    "{\n" +
                    "   \"customStyleSheets\": [\n" +
                    "       \"" + cssFile + "\"\n" +
                    "   ],\n" +
                    "   \"customAssets\": [\n" +
                    "       \"" + logoFile + "\"\n" +
                    "   ]\n" +
                    "}"
                );
            });
        });

        Task task = project.getTasks().getByName(TASK_NAME);
        task.getInputs().file(cssFile);
        task.getInputs().file(logoFile);

        //TODO: is there a better way to set resource files as inputs of the task?
    }

    private static DokkatooExtension getDokkatooExtension(Project project) {
        return project.getExtensions().getByType(DokkatooExtension.class);
    }

}
