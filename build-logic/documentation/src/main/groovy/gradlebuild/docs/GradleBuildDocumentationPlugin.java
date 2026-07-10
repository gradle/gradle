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

import gradlebuild.basics.PublicApi;
import gradlebuild.basics.PublicKotlinDslApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

public abstract class GradleBuildDocumentationPlugin implements Plugin<Project> {

    @Inject
    protected abstract ProviderFactory getProviders();

    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();
        ObjectFactory objects = project.getObjects();

        GradleDocumentationExtension extension = project.getExtensions().create("gradleDocumentation", GradleDocumentationExtension.class);
        applyConventions(project, tasks, objects, layout, extension);

        extension.getQuickFeedback().convention(getProviders().gradleProperty("quickDocs").map(x -> true).orElse(false));

        project.apply(target -> target.plugin(GradleReleaseNotesPlugin.class));
        project.apply(target -> target.plugin(GradleJavadocsPlugin.class));
        project.apply(target -> target.plugin(GradleKotlinDslReferencePlugin.class));
        project.apply(target -> target.plugin(GradleDslReferencePlugin.class));

        checkDocumentation(tasks, extension);
    }

    private void applyConventions(Project project, TaskContainer tasks, ObjectFactory objects, ProjectLayout layout, GradleDocumentationExtension extension) {

        // Stages the three reference trees (javadoc, kotlin-dsl, dsl) for consumption by :docs-site,
        // which composes them with its own user-guide content into the published site.
        tasks.register("stageReferenceDocs", Sync.class, task -> {
            task.from(extension.getJavadocs().getRenderedDocumentation(), sub -> sub.into("javadoc"));
            task.from(extension.getKotlinDslReference().getRenderedDocumentation(), sub -> sub.into("kotlin-dsl"));
            task.from(extension.getDslReference().getRenderedDocumentation(), sub -> sub.into("dsl"));
            task.into(layout.getBuildDirectory().dir("references"));
        });

        extension.getSourceRoot().convention(layout.getProjectDirectory().dir("src/docs"));
        extension.getStagingRoot().convention(layout.getBuildDirectory().dir("working"));

        ConfigurableFileTree css = objects.fileTree();
        css.from(extension.getSourceRoot().dir("css"));
        css.include("*.css");
        extension.getCssFiles().from(css);

        Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");
        Configuration sourcesPath = project.getConfigurations().create("sourcesPath");
        sourcesPath.attributes(a -> {
            a.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            a.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
            a.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "gradle-source-folders"));
        });
        sourcesPath.setCanBeConsumed(false);
        sourcesPath.setCanBeResolved(true);
        sourcesPath.extendsFrom(runtimeClasspath);

        extension.getClasspath().from(runtimeClasspath);
        extension.getSourceRoots().from(sourcesPath.getIncoming().artifactView(v -> v.lenient(true)).getFiles());
        extension.getDocumentedSource().from(sourcesPath.getIncoming().artifactView(v -> v.lenient(true)).getFiles().getAsFileTree().matching(f -> {
            f.include(PublicApi.INSTANCE.getIncludes());
            // Filter out any non-public APIs
            f.exclude(PublicApi.INSTANCE.getExcludes());
        }));
        extension.getKotlinDslSource().from(sourcesPath.getIncoming().artifactView(v -> v.lenient(true)).getFiles().getAsFileTree().matching(f -> {
            f.include(PublicKotlinDslApi.INSTANCE.getIncludes());
            // Filter out any non-public APIs
            f.exclude(PublicKotlinDslApi.INSTANCE.getExcludes());
        }));
    }

    private void checkDocumentation(TaskContainer tasks, GradleDocumentationExtension extension) {
        tasks.named("test", Test.class).configure(task -> {
            task.getInputs().file(extension.getReleaseNotes().getRenderedDocumentation()).withPropertyName("releaseNotes").withPathSensitivity(PathSensitivity.NONE);

            task.getInputs().property("systemProperties", Collections.emptyMap());
            task.getJvmArgumentProviders().add((CommandLineArgumentProvider) () -> List.of(
                "-Dorg.gradle.docs.releasenotes.rendered=" + extension.getReleaseNotes().getRenderedDocumentation().get().getAsFile()
            ));
        });
    }
}
