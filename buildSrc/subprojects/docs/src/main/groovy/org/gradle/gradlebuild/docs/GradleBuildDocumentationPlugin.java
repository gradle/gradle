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

import org.asciidoctor.gradle.AsciidoctorTask;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.build.docs.dsl.source.ExtractDslMetaDataTask;
import org.gradle.build.docs.dsl.source.GenerateDefaultImportsTask;
import org.gradle.gradlebuild.ProjectGroups;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GradleBuildDocumentationPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        GradleDocumentationExtension extension = project.getExtensions().create("gradleDocumentation", GradleDocumentationExtension.class);
        applyConventions(project, tasks, layout, extension);

        project.apply(target -> target.plugin(GradleReleaseNotesPlugin.class));
        project.apply(target -> target.plugin(GradleJavadocsPlugin.class));
        project.apply(target -> target.plugin(GradleDslReferencePlugin.class));

        // TODO: This should be wired through the model
        TaskProvider<ExtractDslMetaDataTask> dslMetaData = tasks.named("dslMetaData", ExtractDslMetaDataTask.class);
        TaskProvider<GenerateDefaultImportsTask> defaultImports = tasks.register("defaultImports", GenerateDefaultImportsTask.class, task -> {
            task.getMetaDataFile().convention(dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile));
            task.getImportsDestFile().convention(layout.getBuildDirectory().file("generated-imports/default-imports.txt"));
            task.getMappingDestFile().convention(layout.getBuildDirectory().file("generated-imports/api-mapping.txt"));

            List<String> excludedPackages = new ArrayList<>();
            // These are part of the API, but not the DSL
            excludedPackages.add("org.gradle.tooling.**");
            excludedPackages.add("org.gradle.testfixtures.**");

            // Tweak the imports due to some inconsistencies introduced before we automated the default-imports generation
            excludedPackages.add("org.gradle.plugins.ide.eclipse.model");
            excludedPackages.add("org.gradle.plugins.ide.idea.model");
            excludedPackages.add("org.gradle.api.tasks.testing.logging");

            // TODO - rename some incubating types to remove collisions and then remove these exclusions
            excludedPackages.add("org.gradle.plugins.binaries.model");

            // Exclude classes that were moved in a different package but the deprecated ones are not removed yet
            excludedPackages.add("org.gradle.platform.base.test");

            task.getExcludedPackages().convention(excludedPackages);
        });
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main", main -> {
            // TODO:
            //  sourceSets.main.output.dir generatedResourcesDir, builtBy: [defaultImports, copyReleaseFeatures]
//            main.getOutput().dir(defaultImports);
        });

        addUtilityTasks(tasks, extension);

        checkDocumentation(layout, tasks, extension);

        // Move to plugin
        generateUserManual(project, tasks, extension);
    }

    private void applyConventions(Project project, TaskContainer tasks, ProjectLayout layout, GradleDocumentationExtension extension) {
        TaskProvider<Sync> stageDocs = tasks.register("stageDocs", Sync.class, task -> {
            // release notes goes in the root of the docs
            task.from(extension.getReleaseNotes().getRenderedDocumentation());

            // DSL reference goes into dsl/
            task.from(extension.getDslReference().getRenderedDocumentation(), sub -> sub.into("dsl"));

            // Javadocs reference goes into javadoc/
            task.from(extension.getJavadocs().getRenderedDocumentation(), sub -> sub.into("javadoc"));

            // TODO: user manual goes into userguide/ (for historical reasons)
            // task.from(extension.getDslReference().getRenderedDocumentation(), sub -> sub.into("userguide"));

            task.into(extension.getDocumentationRenderedRoot());
        });

        extension.getSourceRoot().convention(layout.getProjectDirectory().dir("src/docs"));
        extension.getDocumentationRenderedRoot().convention(layout.getBuildDirectory().dir("docs"));
        extension.getStagingRoot().convention(layout.getBuildDirectory().dir("docs-working"));

        extension.getRenderedDocumentation().from(stageDocs);

        Configuration gradleApiRuntime = project.getConfigurations().create("gradleApiRuntime");
        extension.getClasspath().from(gradleApiRuntime);
        // TODO: This should not reach across project boundaries
        for (Project publicProject : ProjectGroups.INSTANCE.getPublicJavaProjects(project)) {
            extension.getDocumentedSource().from(publicProject.getExtensions().getByType(SourceSetContainer.class).getByName("main").getAllJava());
        }
    }

    private void addUtilityTasks(TaskContainer tasks, GradleDocumentationExtension extension) {
        tasks.register("serveDocs", Exec.class, task -> {
            task.setDescription("Runs a local webserver to serve generated documentation.");
            task.setGroup("documentation");

            int webserverPort = 8000;
            task.workingDir(extension.getDocumentationRenderedRoot());
            task.executable("python");
            task.args("-m", "SimpleHTTPServer", webserverPort);

            task.dependsOn(extension.getRenderedDocumentation());

            //noinspection Convert2Lambda
            task.doFirst(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    task.getLogger().lifecycle("ctrl+C to restart, serving Gradle docs at http://localhost:" + webserverPort);
                }
            });
        });

        tasks.register("docs", task -> {
            task.setDescription("Generates all documentation");
            task.setGroup("documentation");
            task.dependsOn(extension.getRenderedDocumentation());
        });
    }

    private void checkDocumentation(ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<FindBrokenInternalLinks> checkDeadInternalLinks = tasks.register("checkDeadInternalLinks", FindBrokenInternalLinks.class, task -> {
            // TODO: Configure this properly
            task.getReportFile().convention(layout.getBuildDirectory().file("reports/dead-internal-links.txt"));
            task.getDocumentationRoot().convention(extension.getDocumentationRenderedRoot());
            // TODO: This should be the intermediate adoc files
            task.getDocumentationFiles().from();
//            dependsOn(userguideFlattenSources)
//            inputDirectory.set(userguideFlattenSources.get().destinationDir)
        });

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(checkDeadInternalLinks));

        tasks.named("test", Test.class).configure(task -> {
            task.getInputs().file(extension.getReleaseNotes().getRenderedDocumentation()).withPropertyName("releaseNotes").withPathSensitivity(PathSensitivity.NONE);
            task.getInputs().file(extension.getReleaseFeatures().getReleaseFeaturesFile()).withPropertyName("releaseFeatures").withPathSensitivity(PathSensitivity.NONE);

            task.getInputs().property("systemProperties", Collections.emptyMap());
            // TODO: This breaks the provider
            task.systemProperty("org.gradle.docs.releasenotes.rendered", extension.getReleaseNotes().getRenderedDocumentation().get().getAsFile());
            // TODO: This breaks the provider
            task.systemProperty("org.gradle.docs.releasefeatures", extension.getReleaseFeatures().getReleaseFeaturesFile().get().getAsFile());
        });
    }

    private void generateUserManual(Project project, TaskContainer tasks, GradleDocumentationExtension extension) {
        //        def imageFiles = fileTree(userguideSrcDir) {
//            include "img/*.png"
//            include "img/*.gif"
//            include "img/*.jpg"
//        }
//        def resourceFiles = imageFiles + cssFiles

        tasks.withType(AsciidoctorTask.class).configureEach(task -> {
            if (task.getName().equals("asciidoctor")) {
                // ignore this task
                return;
            }

            task.setSeparateOutputDirs(false);
            task.options(Collections.singletonMap("doctype", "book"));

            // TODO: Break the paths assumed here
            TaskInputs inputs = task.getInputs();
            inputs.file("src/docs/css/manual.css")
                    .withPropertyName("manual")
                    .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir("src/main/resources")
                    .withPropertyName("resources")
                    .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir("src/snippets")
                    .withPropertyName("snippets")
                    .withPathSensitivity(PathSensitivity.RELATIVE);


            Map<String, Object> attributes = new HashMap<>();
            // TODO: Break the paths assumed here
            attributes.put("stylesdir", "css/");
            attributes.put("stylesheet", "manual.css");
            attributes.put("imagesdir", "img");
            attributes.put("nofooter", true);
            attributes.put("sectanchors", true);
            attributes.put("sectlinks", true);
            attributes.put("linkattrs", true);
            attributes.put("reproducible", "");
            attributes.put("docinfo", "");
            attributes.put("lang", "en-US");
            attributes.put("encoding", "utf-8");
            attributes.put("idprefix", "");
            attributes.put("website", "https://gradle.org");
            // TODO: This breaks the provider
            attributes.put("javaApi", extension.getJavadocs().getJavaApi().get().toString());
            attributes.put("jdkDownloadUrl", "https://jdk.java.net/");
            // TODO: This is coupled to extension.getJavadocs().getJavaApi()
            attributes.put("javadocReferenceUrl", "https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html");
            // TODO: This is coupled to extension.getJavadocs().getJavaApi()
            attributes.put("minJdkVersion", "8");

            attributes.put("antManual", "https://ant.apache.org/manual");
            attributes.put("docsUrl", "https://docs.gradle.org");
            attributes.put("guidesUrl", "https://guides.gradle.org");

            // TODO: This breaks if the version is changed later.
            attributes.put("gradleVersion", project.getVersion().toString());
            attributes.put("samplesPath", "snippets");
            // Used by SampleIncludeProcessor from `gradle/dotorg-docs`
            attributes.put("samples-dir", "src/snippets");
            task.attributes(attributes);
        });

        // TODO: change generatedResourcesDir to a provider
        // def generatedResourcesDir = gradlebuildJava.generatedResourcesDir
        TaskProvider<Sync> userguideFlattenSources = tasks.register("userguideFlattenSources", Sync.class, task -> {
            task.setDuplicatesStrategy(DuplicatesStrategy.FAIL);
            task.into(extension.getUserManual().getStagingDirectory());

            task.from(extension.getUserManual().getDocumentationSource());
            task.from("src/docs/css", sub -> {
                sub.into("css");
            });
            task.from("src/snippets", sub -> {
                sub.into("snippets");
            });
            task.from("src/docs/userguide", sub -> {
                sub.include("img/**");
            });

            // TODO: What are these?
            // dependsOn css, defaultImports
//            from(generatedResourcesDir) {
//                into(generatedResourcesDir.name)
//            }
//            doLast {
//                adocFiles.each { adocFile ->
//                        file("${buildDir}/userguide-resources/${adocFile.name.substring(0, adocFile.name.length() - 5)}-docinfo.html").text =
//                                """<meta name="adoc-src-path" content="${adocFile.path - adocDir.path}">"""
//                }
//            }
        });

        TaskProvider<AsciidoctorTask> userguideSinglePage = tasks.register("userguideSinglePage", AsciidoctorTask.class, task -> {

//            dependsOn(userguideFlattenSources)
//            sourceDir = userguideFlattenSources.get().destinationDir
//            sources { include "userguide_single.adoc" }
//            outputDir = userguideSinglePageOutputDir
//            backends = ["pdf", "html5"]
//            jvmArgs = ["-Xms3g", "-Xmx3g"]
//
//            attributes toc          : "macro",
//                    toclevels           : 2,
//                    groovyDslPath       : "https://docs.gradle.org/${version}/dsl",
//                    javadocPath         : "https://docs.gradle.org/${version}/javadoc",
//                    kotlinDslPath       : "https://gradle.github.io/kotlin-dsl-docs/api",
//                    "source-highlighter": "coderay"
        });

        TaskProvider<AsciidoctorTask> userguideMultiPage = tasks.register("userguideMultiPage", AsciidoctorTask.class, task -> {
//            dependsOn(userguideFlattenSources)
//            sourceDir = userguideFlattenSources.get().destinationDir
//
//            sources {
//                include "*.adoc"
//                exclude "javaProject*Layout.adoc"
//                exclude "userguide_single.adoc"
//            }
//            outputDir = userguideIntermediateOutputDir
//
//            backends = ["html5"]
//
//            attributes icons        :"font",
//                    "source-highlighter":"prettify",
//                    toc                 :"auto",
//                    toclevels           :1,
//                    "toc-title"         :"Contents",
//                    groovyDslPath       :"../dsl",
//                    javadocPath         :"../javadoc",
//                    kotlinDslPath       :"https://gradle.github.io/kotlin-dsl-docs/api"
        });

        TaskProvider<AsciidoctorTask> distDocs = tasks.register("distDocs", AsciidoctorTask.class, task -> {
//            sourceDir = userguideSrcDir
//            outputDir = distDocsDir
//            sources { include "getting-started.adoc" }
//            backends = ["html5"]
        });

        // Avoid overlapping outputs by copying exactly what we want from other intermediate tasks
        tasks.register("userguide", Sync.class, task -> {
//            dependsOn userguideMultiPage, userguideSinglePage
//            description = "Generates the userguide HTML and PDF"
//            group = "documentation"
//
//            from resourceFiles
//            from userguideIntermediateOutputDir
//            from userguideSinglePageOutputDir
//
//            into userguideDir
//            rename "userguide_single.pdf", "userguide.pdf"
        });
    }
}
