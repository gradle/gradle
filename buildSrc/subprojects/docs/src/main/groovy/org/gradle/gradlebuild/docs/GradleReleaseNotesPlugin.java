/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.gradlebuild.docs;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.nio.charset.Charset;

/**
 * Opinionated plugin that generates the release notes for a Gradle release.
 */
public class GradleReleaseNotesPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        // TODO: Maybe eventually convert this asciidoc too, so everything uses the same markup language.
        generateReleaseNotes(project, layout, tasks, extension);
        generateReleaseFeatures(project, layout, tasks, extension);
    }

    private void generateReleaseNotes(Project project, ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<RenderMarkdown> releaseNotesMarkdown = tasks.register("releaseNotesMarkdown", RenderMarkdown.class, task -> {
            task.setGroup("release notes");
            task.setDescription("Generate release notes HTML page from Markdown.");

            task.getInputEncoding().convention(Charset.defaultCharset().name());
            task.getOutputEncoding().convention(Charset.defaultCharset().name());

            task.getMarkdownFile().convention(extension.getReleaseNotes().getMarkdownFile());
            // TODO: Does this path make sense?
            task.getDestinationFile().convention(layout.getBuildDirectory().file("release-notes-raw/release-notes.html"));
        });

        Configuration jquery = project.getConfigurations().create("jquery");

        TaskProvider<DecorateReleaseNotes> releaseNotesPostProcess = tasks.register("releaseNotes", DecorateReleaseNotes.class, task -> {
            task.setGroup("release notes");
            task.setDescription("Transforms generated release notes.");
            task.getHtmlFile().convention(releaseNotesMarkdown.flatMap(RenderMarkdown::getDestinationFile));
            // TODO: These should be in the model
            task.getBaseStylesheetFile().convention(extension.getDocumentationSourceRoot().file("css/base.css"));
            task.getReleaseNotesJavascriptFile().convention(extension.getDocumentationSourceRoot().file("release/content/script.js"));
            task.getReleaseNotesStylesheetFile().convention(extension.getDocumentationSourceRoot().file("css/release-notes.css"));
            task.getJquery().from(jquery);

            task.getReplacementTokens().put("version", project.provider(() -> String.valueOf(project.getVersion())));
            task.getReplacementTokens().put("baseVersion", project.provider(() -> String.valueOf(project.getRootProject().getExtensions().getExtraProperties().get("baseVersion"))));

            // TODO: Does this path make sense?
            task.getDestinationFile().convention(layout.getBuildDirectory().file("release-notes/release-notes.html"));
        });

        extension.releaseNotes(releaseNotes -> {
            releaseNotes.getMarkdownFile().convention(extension.getDocumentationSourceRoot().file("release/notes.md"));
            releaseNotes.getRenderedFile().convention(releaseNotesPostProcess.flatMap(DecorateReleaseNotes::getDestinationFile));
        });
    }

    private void generateReleaseFeatures(Project project, ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<Sync> copyReleaseFeatures = tasks.register("copyReleaseFeatures", Sync.class, task -> {
            task.from(extension.getReleaseFeatures().getReleaseFeaturesFile());
            task.into(layout.getBuildDirectory().dir("generated-release-features"));
        });

        extension.releaseFeatures(releaseFeatures -> {
            releaseFeatures.getReleaseFeaturesFile().convention(extension.getDocumentationSourceRoot().file("release/release-features.txt"));
        });

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main", main -> main.getResources().srcDirs(copyReleaseFeatures));
    }
}
