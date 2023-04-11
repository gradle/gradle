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
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.util.Collections;

public class GradleBuildDocumentationPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();
        ObjectFactory objects = project.getObjects();
        ProviderFactory providers = project.getProviders();

        GradleDocumentationExtension extension = project.getExtensions().create("gradleDocumentation", GradleDocumentationExtension.class);
        applyConventions(project, tasks, objects, layout, extension);

        extension.getQuickFeedback().convention(providers.provider(() -> project.hasProperty("quickDocs")));

        project.apply(target -> target.plugin(GradleReleaseNotesPlugin.class));
        project.apply(target -> target.plugin(GradleJavadocsPlugin.class));
        project.apply(target -> target.plugin(GradleKotlinDslReferencePlugin.class));
        project.apply(target -> target.plugin(GradleDslReferencePlugin.class));
        project.apply(target -> target.plugin(GradleUserManualPlugin.class));

        addUtilityTasks(tasks, extension);

        checkDocumentation(tasks, extension);
    }

    private void applyConventions(Project project, TaskContainer tasks, ObjectFactory objects, ProjectLayout layout, GradleDocumentationExtension extension) {

        TaskProvider<Sync> stageDocs = tasks.register("stageDocs", Sync.class, task -> {
            // release notes goes in the root of the docs
            task.from(extension.getReleaseNotes().getRenderedDocumentation());

            // DSL reference goes into dsl/
            task.from(extension.getDslReference().getRenderedDocumentation(), sub -> sub.into("dsl"));

            // Javadocs reference goes into javadoc/
            task.from(extension.getJavadocs().getRenderedDocumentation(), sub -> sub.into("javadoc"));

            // Dokka Kotlin DSL reference goes into kotlin-dsl/
            task.from(extension.getKotlinDslReference().getRenderedDocumentation(), sub -> sub.into("kotlin-dsl"));

            // User manual goes into userguide/ (for historical reasons)
            task.from(extension.getUserManual().getRenderedDocumentation(), sub -> sub.into("userguide"));

            task.into(extension.getDocumentationRenderedRoot());
        });

        extension.getSourceRoot().convention(layout.getProjectDirectory().dir("src/docs"));
        extension.getDocumentationRenderedRoot().convention(layout.getBuildDirectory().dir("docs"));
        extension.getStagingRoot().convention(layout.getBuildDirectory().dir("working"));

        ConfigurableFileTree css = objects.fileTree();
        css.from(extension.getSourceRoot().dir("css"));
        css.include("*.css");
        extension.getCssFiles().from(css);

        extension.getRenderedDocumentation().from(stageDocs);

        Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");
        Configuration sourcesPath = project.getConfigurations().create("sourcesPath");
        sourcesPath.attributes(a -> {
            a.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            a.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
            a.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "gradle-source-folders"));
        });
        sourcesPath.setCanBeConsumed(false);
        sourcesPath.setCanBeResolved(true);
        sourcesPath.setVisible(false);
        sourcesPath.extendsFrom(runtimeClasspath);

        extension.getClasspath().from(runtimeClasspath);
        extension.getDocumentedSource().from(sourcesPath.getIncoming().artifactView(v -> v.lenient(true)).getFiles().getAsFileTree().matching(f -> {
            // Filter out any non-public APIs
            f.include(PublicApi.INSTANCE.getIncludes());
            f.exclude(PublicApi.INSTANCE.getExcludes());
        }));
        extension.getKotlinDslSource().from(sourcesPath.getIncoming().artifactView(v -> v.lenient(true)).getFiles().getAsFileTree().matching(f -> {
            // Filter out any non-public APIs
            f.include(PublicApi.INSTANCE.getIncludes());
            f.include(PublicKotlinDslApi.INSTANCE.getIncludes());
            f.exclude(PublicApi.INSTANCE.getExcludes());
            f.exclude(PublicKotlinDslApi.INSTANCE.getExcludes());
        }));
    }

    private void addUtilityTasks(TaskContainer tasks, GradleDocumentationExtension extension) {
        tasks.register("serveDocs", JavaExec.class, task -> {
            task.setDescription("Runs a local webserver to serve generated documentation.");
            task.setGroup("documentation");

            int webserverPort = 8000;
            task.getJavaLauncher().set(
                task.getProject().getExtensions().getByType(JavaToolchainService.class)
                    .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(18)))
            );
            task.workingDir(extension.getDocumentationRenderedRoot());
            task.getMainModule().set("jdk.httpserver");
            task.args("-p", String.valueOf(webserverPort));

            task.dependsOn(extension.getRenderedDocumentation());
        });

        tasks.register("docs", task -> {
            task.setDescription("Generates all documentation");
            task.setGroup("documentation");
            task.dependsOn(extension.getRenderedDocumentation());
        });
    }

    private void checkDocumentation(TaskContainer tasks, GradleDocumentationExtension extension) {
        tasks.named("test", Test.class).configure(task -> {
            task.getInputs().file(extension.getReleaseNotes().getRenderedDocumentation()).withPropertyName("releaseNotes").withPathSensitivity(PathSensitivity.NONE);

            task.getInputs().property("systemProperties", Collections.emptyMap());
            // TODO: This breaks the provider
            task.systemProperty("org.gradle.docs.releasenotes.rendered", extension.getReleaseNotes().getRenderedDocumentation().get().getAsFile());
        });
    }
}
