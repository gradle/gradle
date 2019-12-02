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
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.build.docs.Docbook2Xhtml;
import org.gradle.build.docs.UserGuideTransformTask;
import org.gradle.build.docs.dsl.docbook.AssembleDslDocTask;
import org.gradle.build.docs.dsl.source.ExtractDslMetaDataTask;
import org.gradle.build.docs.dsl.source.GenerateDefaultImportsTask;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.gradlebuild.BuildEnvironment;
import org.gradle.gradlebuild.ProjectGroups;
import org.gradle.gradlebuild.PublicApi;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.io.File;
import java.nio.charset.Charset;
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
        ObjectFactory objects = project.getObjects();

        GradleDocumentationExtension extension = project.getExtensions().create("gradleDocumentation", GradleDocumentationExtension.class);
        applyConventions(project, layout, extension);

        generateReleaseNotes(project, layout, tasks, extension);
        generateReleaseFeatures(project, extension);
        generateJavadocs(project, layout, tasks, extension);
        generateDslReference(project, layout, tasks, objects, extension);
        generateUserManual(project, tasks, extension);

        addUtilityTasks(tasks, extension);

        checkDocumentation(layout, tasks, extension);
    }

    private void generateUserManual(Project project, TaskContainer tasks, GradleDocumentationExtension extension) {
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

    private void applyConventions(Project project, ProjectLayout layout, GradleDocumentationExtension extension) {
        extension.getDocumentationSourceRoot().convention(layout.getProjectDirectory().dir("src/docs"));
        extension.getDocumentationRenderedRoot().convention(layout.getBuildDirectory().dir("docs"));

        Configuration gradleApiRuntime = project.getConfigurations().create("gradleApiRuntime");
        extension.getClasspath().from(gradleApiRuntime);
        // TODO: This should not reach across project boundaries
        for (Project publicProject : ProjectGroups.INSTANCE.getPublicJavaProjects(project)) {
            extension.getSource().from(publicProject.getExtensions().getByType(SourceSetContainer.class).getByName("main").getAllJava());
        }
    }

    private void generateDslReference(Project project, ProjectLayout layout, TaskContainer tasks, ObjectFactory objects, GradleDocumentationExtension extension) {
        // TODO: Is this needed still?
        TaskProvider<Sync> css = tasks.register("css", Sync.class, task -> {
            task.into(layout.getBuildDirectory().dir("css"));
            task.from(extension.getDocumentationSourceRoot().dir("css"));
            task.include("*.css");
            task.include("*.svg");
        });

//        def imageFiles = fileTree(userguideSrcDir) {
//            include "img/*.png"
//            include "img/*.gif"
//            include "img/*.jpg"
//        }
//        def resourceFiles = imageFiles + cssFiles

        Configuration userGuideStyleSheetConf = project.getConfigurations().create("userGuideStyleSheets");
//        TaskProvider<Sync> userGuideStyleSheets = tasks.register("userguideStyleSheets", Sync.class, task -> {
//            File stylesheetsDir = new File(srcDocsDir, "stylesheets")
//            into new File(buildDir, "stylesheets")
//            from(stylesheetsDir) {
//                include "**/*.xml"
//                include "*.xsl"
//            }
//            from(cssFiles)
//            from({zipTree(userGuideStyleSheetConf.singleFile)}) {
//                // Remove the prefix
//                eachFile {
//                    fcd -> fcd.path = fcd.path.replaceFirst("^docbook-xsl-[0-9\\.]+/", "")
//                }
//            }
//        });

        DslReference dslReference = extension.getDslReference();

        TaskProvider<ExtractDslMetaDataTask> dslMetaData = tasks.register("dslMetaData", ExtractDslMetaDataTask.class, task -> {
            task.source(extension.getSource());
            // TODO: Does this path matter?
            task.getDestinationFile().convention(layout.getBuildDirectory().file("generated-dsl/dsl-meta-data.bin"));
        });

        TaskProvider<AssembleDslDocTask> dslDocbook = tasks.register("dslDocbook", AssembleDslDocTask.class, task -> {
            task.getSources().from(dslReference.getRoot());
            task.getSourceFile().convention(dslReference.getRoot().file("dsl.xml"));
            task.getClassDocbookDirectory().convention(dslReference.getRoot());
            task.getClassMetaDataFile().convention(dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile));
            task.getPluginsMetaDataFile().convention(dslReference.getRoot().file("plugins.xml"));

            // TODO: Do these paths matter?
            task.getDestFile().convention(layout.getBuildDirectory().file("generated-dsl/dsl.xml"));
            task.getLinksFile().convention(layout.getBuildDirectory().file("generated-dsl/api-links.bin"));
        });

        TaskProvider<UserGuideTransformTask> dslStandaloneDocbook = tasks.register("dslStandaloneDocbook", UserGuideTransformTask.class, task -> {
            task.getVersion().convention(project.provider(() -> project.getVersion().toString()));
            task.getSourceFile().convention(dslDocbook.flatMap(AssembleDslDocTask::getDestFile));
            task.getLinksFile().convention(dslDocbook.flatMap(AssembleDslDocTask::getLinksFile));

            task.getDsldocUrl().convention("../dsl");
            task.getJavadocUrl().convention("../javadoc");
            task.getWebsiteUrl().convention("https://gradle.org");

            // TODO: Do these paths matter?
            task.getDestFile().convention(layout.getBuildDirectory().file("generated-dsl/dsl-standalone.xml"));
        });

        Configuration userGuideTask = project.getConfigurations().create("userGuideTask");

        TaskProvider<Docbook2Xhtml> dslHtml = tasks.register("dslHtml", Docbook2Xhtml.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generates DSL reference HTML documentation.");

            task.source(dslStandaloneDocbook);
            task.getStylesheetFile().convention(dslReference.getStylesheet());
            task.getStylesheetHighlightFile().convention(dslReference.getHighlightStylesheet());

            task.getResources().from(dslReference.getResources());
            task.getClasspath().from(userGuideTask);

            task.getDestinationDirectory().convention(layout.getBuildDirectory().dir("dsl"));
        });

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

        extension.dslReference(dslRef -> {
            dslRef.getResources().from(css);
            ConfigurableFileTree js = objects.fileTree();
            js.from(dslReference.getRoot());
            js.include("*.js");
            dslRef.getResources().from(js);

            dslRef.getRoot().convention(extension.getDocumentationSourceRoot().dir("dsl"));
            dslRef.getStylesheet().convention(extension.getDocumentationSourceRoot().file("stylesheets/dslHtml.xsl"));
            dslRef.getHighlightStylesheet().convention(extension.getDocumentationSourceRoot().file("stylesheets/custom-highlight/custom-xslthl-config.xml"));

            dslRef.getRenderedDocumentation().convention(dslHtml.flatMap(Docbook2Xhtml::getDestinationDirectory));
        });

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main", main -> {
            // TODO:
            //  sourceSets.main.output.dir generatedResourcesDir, builtBy: [defaultImports, copyReleaseFeatures]
//            main.getOutput().dir(defaultImports);
        });
    }

    private void generateJavadocs(Project project, ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<Javadoc> javadocAll = tasks.register("javadocAll", Javadoc.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generate Javadocs for all API classes");

            // TODO: This breaks if version is changed later
            task.setTitle("Gradle API " + project.getVersion());

            Javadocs javadocs = extension.getJavadocs();

            // TODO: This should be part of Javadoc task
            task.getInputs().file(javadocs.getJavadocCss())
                    .withPropertyName("stylesheetFile")
                    .withPathSensitivity(PathSensitivity.NAME_ONLY);

            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
            options.setEncoding("utf-8");
            options.setDocEncoding("utf-8");
            options.setCharSet("utf-8");

            // TODO: This would be better to model as separate options
            options.addStringOption("Xdoclint:syntax,html,reference", "-quiet");
            // TODO: This breaks the provider
            options.addStringOption("stylesheetfile", javadocs.getJavadocCss().get().getAsFile().getAbsolutePath());
            options.addStringOption("source", "8");
            // TODO: This breaks the provider
            options.links(javadocs.getJavaApi().get().toString(), javadocs.getGroovyApi().get().toString(), javadocs.getMavenApi().get().toString());

            task.source(extension.getSource());

            // TODO: This breaks the provider
            task.include(javadocs.getIncludes().get());
            // TODO: This breaks the provider
            task.exclude(javadocs.getIncludes().get());

            task.setClasspath(extension.getClasspath());

            // TODO: This should be in Javadoc task
            DirectoryProperty generatedJavadocDirectory = project.getObjects().directoryProperty();
            generatedJavadocDirectory.set(layout.getBuildDirectory().dir("javadoc"));
            task.getOutputs().file(generatedJavadocDirectory);
            task.getExtensions().getExtraProperties().set("destinationDirectory", generatedJavadocDirectory);
            // TODO: This breaks the provider
            task.setDestinationDir(generatedJavadocDirectory.get().getAsFile());

            if (BuildEnvironment.INSTANCE.getJavaVersion().isJava11Compatible()) {
                options.addBooleanOption("html4", true);
                options.addBooleanOption("-no-module-directories", true);

                //noinspection Convert2Lambda
                task.doLast(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        project.copy(copySpec -> {
                            // Commit http://hg.openjdk.java.net/jdk/jdk/rev/89dc31d7572b broke use of JSZip (https://bugs.openjdk.java.net/browse/JDK-8214856)
                            // fixed in Java 12 by http://hg.openjdk.java.net/jdk/jdk/rev/b4982a22926b
                            // TODO: Remove this script.js workaround when we distribute Gradle using JDK 12 or higher
                            copySpec.from(extension.getDocumentationSourceRoot().dir("js/javadoc"));

                            // This is a work-around for https://bugs.openjdk.java.net/browse/JDK-8211194. Can be removed once that issue is fixed on JDK"s side
                            // Since JDK 11, package-list is missing from javadoc output files and superseded by element-list file, but a lot of external tools still need it
                            // Here we generate this file manually
                            copySpec.from(generatedJavadocDirectory.file("element-list"), sub -> {
                                sub.rename(t -> "package-list");
                            });
                            copySpec.into(generatedJavadocDirectory);
                        });
                    }
                });
            }
        });

        extension.javadocs(javadocs -> {
            javadocs.getJavadocCss().convention(extension.getDocumentationSourceRoot().file("css/javadoc.css"));
            javadocs.getIncludes().convention(PublicApi.INSTANCE.getIncludes());
            javadocs.getExcludes().convention(PublicApi.INSTANCE.getExcludes());
            // TODO: destinationDirectory should be part of Javadoc
            javadocs.getGeneratedJavaDocs().convention(javadocAll.flatMap(task -> (DirectoryProperty) task.getExtensions().getExtraProperties().get("destinationDirectory")));
        });

        CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
        tasks.register("checkstyleApi", Checkstyle.class, task -> {
            task.source(extension.getSource());
            // TODO: This is ugly
            task.setConfig(project.getResources().getText().fromFile(checkstyle.getConfigDirectory().file("checkstyle-api.xml")));
            task.setClasspath(layout.files());
            task.getReports().getXml().setDestination(new File(checkstyle.getReportsDir(), "checkstyle-api.xml"));
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
            task.getInputs().file(extension.getReleaseNotes().getRenderedFile()).withPropertyName("releaseNotes").withPathSensitivity(PathSensitivity.NONE);
            task.getInputs().file(extension.getReleaseFeatures().getReleaseFeaturesFile()).withPropertyName("releaseFeatures").withPathSensitivity(PathSensitivity.NONE);

            task.getInputs().property("systemProperties", Collections.emptyMap());
            // TODO: This breaks the provider
            task.systemProperty("org.gradle.docs.releasenotes.rendered", extension.getReleaseNotes().getRenderedFile().get().getAsFile());
            // TODO: This breaks the provider
            task.systemProperty("org.gradle.docs.releasefeatures", extension.getReleaseFeatures().getReleaseFeaturesFile().get().getAsFile());
        });
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

    private void generateReleaseFeatures(Project project, GradleDocumentationExtension extension) {
        extension.releaseFeatures(releaseFeatures -> {
            releaseFeatures.getReleaseFeaturesFile().convention(extension.getDocumentationSourceRoot().file("release/release-features.txt"));
        });

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main", main -> {
            // TODO:
            //  sourceSets.main.output.dir generatedResourcesDir, builtBy: [defaultImports, copyReleaseFeatures]
            // main.getOutput().dir();
        });
    }

    private void generateReleaseNotes(Project project, ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<RenderMarkdown> releaseNotesMarkdown = tasks.register("releaseNotesMarkdown", RenderMarkdown.class, task -> {
            task.setGroup("release notes");
            task.setDescription("Generate release notes HTML page from Markdown.");

            task.getInputEncoding().convention(Charset.defaultCharset());
            task.getOutputEncoding().convention(Charset.defaultCharset());

            task.getMarkdownFile().convention(extension.getReleaseNotes().getSourceFile());
            // TODO: Does this path make sense?
            task.getDestinationFile().convention(layout.getBuildDirectory().file("release-notes-raw/release-notes.html"));
        });

        TaskProvider<JsoupPostProcess> releaseNotesPostProcess = tasks.register("releaseNotes", JsoupPostProcess.class, task -> {
            task.setGroup("release notes");
            task.setDescription("Transforms generated release notes.");
            task.getHtmlFile().convention(releaseNotesMarkdown.flatMap(RenderMarkdown::getDestinationFile));
            // TODO: Does this path make sense?
            task.getDestinationFile().convention(layout.getBuildDirectory().file("release-notes/release-notes.html"));
            task.getReplacementTokens().put("version", project.provider(() -> String.valueOf(project.getVersion())));
            task.getReplacementTokens().put("baseVersion", project.provider(() -> String.valueOf(project.getRootProject().getExtensions().getExtraProperties().get("baseVersion"))));

            task.getTransforms().from("src/transforms/release-notes.gradle");
        });

        extension.releaseNotes(releaseNotes -> {
            releaseNotes.getSourceFile().convention(extension.getDocumentationSourceRoot().file("release/notes.md"));
            releaseNotes.getRenderedFile().convention(releaseNotesPostProcess.flatMap(JsoupPostProcess::getDestinationFile));
        });
    }
}
