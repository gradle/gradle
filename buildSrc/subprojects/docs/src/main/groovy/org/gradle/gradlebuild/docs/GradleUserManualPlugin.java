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

import groovy.lang.Closure;
import org.asciidoctor.gradle.AsciidoctorTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GradleUserManualPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        generateUserManual(project, tasks, layout, extension);
    }

    private void generateUserManual(Project project, TaskContainer tasks, ProjectLayout layout, GradleDocumentationExtension extension) {
        tasks.withType(AsciidoctorTask.class).configureEach(task -> {
            if (task.getName().equals("asciidoctor")) {
                // ignore this task
                task.setEnabled(false);
                return;
            }

            task.setSeparateOutputDirs(false);
            task.options(Collections.singletonMap("doctype", "book"));
            task.backends("html5");

            // TODO: Break the paths assumed here
            TaskInputs inputs = task.getInputs();
            inputs.files(extension.getCssFiles())
                    .withPropertyName("manual")
                    .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir("src/main/resources")
                    .withPropertyName("resources")
                    .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.files(extension.getUserManual().getSnippets())
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
            attributes.put("samples-dir", "src/snippets"); // TODO:
            task.attributes(attributes);
        });

        // TODO: change generatedResourcesDir to a provider
        // def generatedResourcesDir = gradlebuildJava.generatedResourcesDir

        TaskProvider<Sync> userguideFlattenSources = tasks.register("stageUserguideSource", Sync.class, task -> {
            task.setDuplicatesStrategy(DuplicatesStrategy.FAIL);

            // Flatten adocs into a single directory
            task.from(extension.getUserManual().getRoot(), sub -> {
                sub.include("**/*.adoc");
                sub.eachFile(fcd -> fcd.setRelativePath(RelativePath.parse(true, fcd.getName())));
            });

            // include images (TODO: maybe these should be counted as "resources")
            task.from(extension.getUserManual().getRoot(), sub -> {
                sub.include("**/*.png");
                sub.include("**/*.gif");
                sub.include("**/*.gif");
                sub.into("img");
            });
            task.from(extension.getUserManual().getSnippets(), sub -> sub.into("snippets"));
            task.from(extension.getCssFiles(), sub -> sub.into("css"));

            task.into(extension.getUserManual().getStagingRoot().dir("raw"));
            // TODO: ???
//            doLast {
//                adocFiles.each { adocFile ->
//                        file("${buildDir}/userguide-resources/${adocFile.name.substring(0, adocFile.name.length() - 5)}-docinfo.html").text =
//                                """<meta name="adoc-src-path" content="${adocFile.path - adocDir.path}">"""
//                }
//            }
        });

        TaskProvider<AsciidoctorTask> userguideSinglePage = tasks.register("userguideSinglePage", AsciidoctorTask.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generates single-page user manual.");
            task.dependsOn(userguideFlattenSources);

            task.sources(new Closure(null) {
                public Object doCall(Object ignore) {
                    ((PatternSet)this.getDelegate()).include("userguide_single.adoc");
                    return null;
                }
            });
            task.backends("pdf");

            // TODO: This breaks the provider
            task.setSourceDir(userguideFlattenSources.get().getDestinationDir());
            // TODO: This breaks the provider
            task.setOutputDir(extension.getUserManual().getStagingRoot().dir("render-single").get().getAsFile());

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("source-highlighter", "coderay");
            attributes.put("toc", "macro");
            attributes.put("toclevels", 2);

            // TODO: This breaks if version is changed later
            attributes.put("groovyDslPath", "https://docs.gradle.org/" + project.getVersion() + "/dsl");
            attributes.put("javadocPath", "https://docs.gradle.org/" + project.getVersion() + "/javadoc");
            attributes.put("kotlinDslPath", "https://gradle.github.io/kotlin-dsl-docs/api");
            task.attributes(attributes);
        });

        TaskProvider<AsciidoctorTask> userguideMultiPage = tasks.register("userguideMultiPage", AsciidoctorTask.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generates multi-page user manual.");
            task.dependsOn(userguideFlattenSources);

            task.sources(new Closure(null) {
                public Object doCall(Object ignore) {
                    ((PatternSet)this.getDelegate()).include("**/*.adoc");
                    ((PatternSet)this.getDelegate()).exclude("javaProject*Layout.adoc");
                    ((PatternSet)this.getDelegate()).exclude("userguide_single.adoc");
                    return null;
                }
            });
            // TODO: This breaks the provider
            task.setSourceDir(userguideFlattenSources.get().getDestinationDir());
            // TODO: This breaks the provider
            task.setOutputDir(extension.getUserManual().getStagingRoot().dir("render-multi").get().getAsFile());

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("icons", "font");
            attributes.put("source-highlighter", "prettify");
            attributes.put("toc", "auto");
            attributes.put("toclevels", 1);
            attributes.put("toc-title", "Contents");
            attributes.put("groovyDslPath", "../dsl");
            attributes.put("javadocPath", "../javadoc");
            attributes.put("kotlinDslPath", "https://gradle.github.io/kotlin-dsl-docs/api");
            task.attributes(attributes);
        });

        // Avoid overlapping outputs by copying exactly what we want from other intermediate tasks
        TaskProvider<Sync> userguide = tasks.register("userguide", Sync.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Stages rendered user manual documentation.");

            task.from(userguideSinglePage);
            task.from(userguideMultiPage);
            task.into(extension.getUserManual().getStagingRoot().dir("final"));

            task.rename("userguide_single.pdf", "userguide.pdf");
// TODO: is this needed?
//            from resourceFiles
        });

        extension.userManual(userManual -> {
            userManual.getRoot().convention(extension.getSourceRoot().dir("userguide"));
            userManual.getStagingRoot().convention(extension.getStagingRoot().dir("usermanual"));
            // TODO:
            //  userManual.getRenderedGettingStartedPage().convention(gettingStarted.flatMap(t -> t.getou));
            userManual.getSnippets().convention(layout.getProjectDirectory().dir("src/snippets"));

            userManual.getRenderedDocumentation().from(userguide);
        });
    }
}
