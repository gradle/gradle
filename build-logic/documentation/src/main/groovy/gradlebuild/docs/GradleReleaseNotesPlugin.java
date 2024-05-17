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

package gradlebuild.docs;

import gradlebuild.buildutils.tasks.AbstractCheckOrUpdateContributorsInReleaseNotes;
import gradlebuild.identity.extension.ModuleIdentityExtension;
import gradlebuild.buildutils.tasks.CheckContributorsInReleaseNotes;
import gradlebuild.buildutils.tasks.UpdateContributorsInReleaseNotes;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.GradleVersion;

import java.nio.charset.Charset;

/**
 * Opinionated plugin that generates the release notes for a Gradle release.
 *
 * TODO: Maybe eventually convert this asciidoc too, so everything uses the same markup language.
 */
public class GradleReleaseNotesPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);

        generateReleaseNotes(project, layout, tasks, extension);
    }

    private void generateReleaseNotes(Project project, ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<RenderMarkdown> releaseNotesMarkdown = tasks.register("releaseNotesMarkdown", RenderMarkdown.class, task -> {
            task.setGroup("release notes");
            task.setDescription("Generate release notes HTML page from Markdown.");

            task.getInputEncoding().convention(Charset.defaultCharset().name());
            task.getOutputEncoding().convention(Charset.defaultCharset().name());

            task.getMarkdownFile().convention(extension.getReleaseNotes().getMarkdownFile());
            task.getDestinationFile().convention(extension.getStagingRoot().file("release-notes/raw.html"));
        });

        TaskProvider<DecorateReleaseNotes> releaseNotesPostProcess = tasks.register("releaseNotes", DecorateReleaseNotes.class, task -> {
            task.setGroup("release notes");
            task.setDescription("Transforms generated release notes.");

            task.getHtmlFile().convention(releaseNotesMarkdown.flatMap(RenderMarkdown::getDestinationFile));
            task.getBaseCssFile().convention(extension.getReleaseNotes().getBaseCssFile());
            task.getReleaseNotesCssFile().convention(extension.getReleaseNotes().getReleaseNotesCssFile());
            task.getReleaseNotesJavascriptFile().convention(extension.getReleaseNotes().getReleaseNotesJsFile());
            task.getJquery().from(extension.getReleaseNotes().getJquery());

            ModuleIdentityExtension moduleIdentity = project.getExtensions().getByType(ModuleIdentityExtension.class);
            MapProperty<String, String> replacementTokens = task.getReplacementTokens();
            replacementTokens.put("version", moduleIdentity.getVersion().map(GradleVersion::getVersion));
            replacementTokens.put("baseVersion", moduleIdentity.getVersion().map(v -> v.getBaseVersion().getVersion()));

            task.getDestinationFile().convention(extension.getStagingRoot().file("release-notes/release-notes.html"));
        });

        tasks.register("checkContributorsInReleaseNotes", CheckContributorsInReleaseNotes.class);
        tasks.register("updateContributorsInReleaseNotes", UpdateContributorsInReleaseNotes.class);
        tasks.withType(AbstractCheckOrUpdateContributorsInReleaseNotes.class).configureEach(task -> {
            task.getGithubToken().set(project.getProviders().environmentVariable("GITHUB_TOKEN"));
            task.getReleaseNotes().set(extension.getReleaseNotes().getMarkdownFile());
            task.getMilestone().convention(project.getProviders().fileContents(project.getRootProject().getLayout().getProjectDirectory().file("version.txt")).getAsText().map(String::trim));
        });

        Configuration jquery = project.getConfigurations().create("jquery", conf -> {
            conf.setDescription("JQuery dependencies embedded by release notes.");
        });

        extension.releaseNotes(releaseNotes -> {
            releaseNotes.getMarkdownFile().convention(extension.getSourceRoot().file("release/notes.md"));
            releaseNotes.getRenderedDocumentation().convention(releaseNotesPostProcess.flatMap(DecorateReleaseNotes::getDestinationFile));
            releaseNotes.getBaseCssFile().convention(extension.getSourceRoot().file("css/base.css"));
            releaseNotes.getReleaseNotesCssFile().convention(extension.getSourceRoot().file("css/release-notes.css"));
            releaseNotes.getReleaseNotesJsFile().convention(extension.getSourceRoot().file("release/content/script.js"));
            releaseNotes.getJquery().from(jquery);
        });
    }
}
