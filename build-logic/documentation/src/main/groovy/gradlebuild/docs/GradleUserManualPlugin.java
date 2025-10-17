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

import gradlebuild.docs.dsl.source.GenerateApiMapping;
import gradlebuild.docs.dsl.source.GenerateDefaultImports;
import org.asciidoctor.gradle.jvm.AsciidoctorTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RelativePath;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.ysb33r.grolifant.api.core.jvm.ExecutionMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

/**
 * Gradle plugin that assembles the Gradle User Manual.
 */
public class GradleUserManualPlugin implements Plugin<Project> {

    public static final String DOCS_GRADLE_ORG = "https://docs.gradle.org/";

    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        // Central configuration object created in the root documentation plugin
        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);

        // Generate “default imports” and “API mapping” files used inside docs
        generateDefaultImports(project, tasks, extension);

        // Configure all tasks needed to build the user manual
        generateUserManual(project, tasks, layout, extension);

        // User manual validation tasks
        checkXrefLinksInUserManualAreValid(layout, tasks, extension);
        checkMultiLangSnippetsAreValid(layout, tasks, extension);
        checkLinksInUserManualAreNotMissing(layout, tasks, extension);
    }

    /**
     * Packages that should not be auto-imported or mapped in the DSL docs.
     * These are excluded from the generated helper artifacts used by Asciidoc processors.
     */
    public static List<String> getDefaultExcludedPackages() {
        // TODO: This should be configured via the extension vs hardcoded in the plugin
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
        return excludedPackages;
    }

    // TODO: This doesn't really make sense to be part of the user manual generation, but it's so tied up into it
    // it's left here for a future project.
    private void generateDefaultImports(Project project, TaskContainer tasks, GradleDocumentationExtension extension) {
        List<String> excludedPackages = getDefaultExcludedPackages();

        // Staging folder for generated helper files used during Asciidoctor rendering
        Provider<Directory> generatedDirectory = extension.getUserManual().getStagingRoot().dir("generated");

        // Produces a mapping file used by custom processors to link API symbols
        TaskProvider<GenerateApiMapping> apiMapping = tasks.register("apiMapping", GenerateApiMapping.class, task -> {
            task.getMetaDataFile().convention(extension.getDslReference().getGeneratedMetaDataFile());
            task.getMappingDestFile().convention(generatedDirectory.map(dir -> dir.file("api-mapping.txt")));
            task.getExcludedPackages().convention(excludedPackages);
        });

        // Produces a default-imports file consumed by the docs (e.g., to simplify code snippets)
        TaskProvider<GenerateDefaultImports> defaultImports = tasks.register("defaultImports", GenerateDefaultImports.class, task -> {
            task.getMetaDataFile().convention(extension.getDslReference().getGeneratedMetaDataFile());
            task.getImportsDestFile().convention(generatedDirectory.map(dir -> dir.file("default-imports.txt")));
            task.getExcludedPackages().convention(excludedPackages);
        });

        // Make the generated directory part of the main source set’s outputs so downstream tasks can see it
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main", main ->
            main.getOutput().dir(singletonMap("builtBy", asList(apiMapping, defaultImports)), generatedDirectory)
        );

        // Also expose these files as "resources" to the user manual pipeline
        extension.getUserManual().getResources().from(apiMapping);
        extension.getUserManual().getResources().from(defaultImports);
    }

    private void generateUserManual(Project project, TaskContainer tasks, ProjectLayout layout, GradleDocumentationExtension extension) {
        // Configure all Asciidoctor tasks used to produce the user manual
        tasks.withType(AsciidoctorTask.class).configureEach(task -> {
            if (task.getName().equals("asciidoctor")) {
                // The plugin applies the Asciidoctor plugin globally, but we do not use its default task.
                task.setEnabled(false);
                return;
            }

            // Run Asciidoctor in a separate process for stability/performance
            task.setExecutionMode(ExecutionMode.OUT_OF_PROCESS);
            task.outputOptions(options -> {
                options.setSeparateOutputDirs(false);
                options.setBackends(singletonList("html5"));
            });

            // Inputs that affect rendering (changes will invalidate outputs appropriately)
            // TODO: Break the paths assumed here
            TaskInputs inputs = task.getInputs();
            inputs.files(extension.getCssFiles())
                .withPropertyName("manual")
                .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir("src/main/resources")
                .withPropertyName("resources")
                .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir(extension.getUserManual().getSnippets())
                .withPropertyName("snippets")
                .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir(extension.getUserManual().getSamples())
                .withPropertyName("samples")
                .withPathSensitivity(PathSensitivity.RELATIVE);

            // Styles are copied into the staged tree so Asciidoctor can use a simple relative path
            Provider<Directory> stylesDir = extension.getUserManual().getStagedDocumentation().dir("css");
            inputs.dir(stylesDir)
                .withPropertyName("stylesdir")
                .withPathSensitivity(PathSensitivity.RELATIVE);

            // Common attributes injected into every AsciidoctorTask in this project
            // TODO: Break the paths assumed here
            Map<String, Object> attributes = new HashMap<>();
            // TODO: This breaks the provider
            attributes.put("stylesdir", stylesDir.get().getAsFile().getAbsolutePath());
            attributes.put("stylesheet", "manual.css");
            attributes.put("doctype", "book");
            attributes.put("imagesdir", "img");
            attributes.put("nofooter", true);
            attributes.put("javadocPath", "../javadoc");
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
            attributes.put("minJdkVersion", "17");

            attributes.put("antManual", "https://ant.apache.org/manual");
            attributes.put("docsUrl", "https://docs.gradle.org");

            // Versioned attributes (used by links and banners in the manual)
            // TODO: This breaks if the version is changed later.
            attributes.put("gradleVersion", project.getVersion().toString());
            attributes.put("gradleVersion90", "9.0.0");
            attributes.put("gradleVersion8", "8.14.3");

            // Paths inside the staged tree for snippet/sample inclusion
            attributes.put("snippetsPath", "snippets");
            // Make sure the 'raw' location of the samples is available in all AsciidoctorTasks to access files with expected outputs in the 'tests' folder for inclusion in READMEs
            attributes.put("samplesPath", extension.getUserManual().getStagingRoot().dir("raw/samples").get().getAsFile());

            task.attributes(attributes);
        });

        // Generates docinfo files (Asciidoctor “docinfo” header/footer snippets) based on the sources
        TaskProvider<GenerateDocInfo> generateDocinfo = tasks.register("generateDocInfo", GenerateDocInfo.class, task -> {
            task.getDocumentationFiles().from(extension.getUserManual().getRoot());
            task.getDocumentationRoot().convention(extension.getUserManual().getRoot());
            task.getDestinationDirectory().convention(layout.getBuildDirectory().dir("tmp/" + task.getName()));
        });

        // Stages a flattened, clean “raw” Asciidoc tree that Asciidoctor consumes
        TaskProvider<Sync> userguideFlattenSources = tasks.register("stageUserguideSource", Sync.class, task -> {
            task.setDuplicatesStrategy(DuplicatesStrategy.FAIL);

            // Flatten all .adoc into a single directory (filenames must be unique)
            // TODO: This doesn't allow adoc files to be generated?
            task.from(extension.getUserManual().getRoot(), sub -> {
                sub.include("**/*.adoc");
                // Flatten adocs into a single directory
                sub.eachFile(fcd -> fcd.setRelativePath(RelativePath.parse(true, fcd.getName())));
            });

            // Copy snippets/samples but strip build outputs and Gradle internals
            // (filter out files generated if the build contained was ever executed)
            task.from(extension.getUserManual().getSnippets(), sub -> {
                sub.into("snippets");
                sub.exclude("**/.gradle/**");
                sub.exclude("**/build/**");
                sub.setIncludeEmptyDirs(false);
            });
            task.from(extension.getUserManual().getSamples(), sub -> {
                sub.into("samples");
                sub.exclude("**/*.adoc");
                sub.exclude("**/.gradle/**");
                sub.exclude("**/build/**");
                sub.setIncludeEmptyDirs(false);
            });

            // Static assets: css + images
            task.from(extension.getCssFiles(), sub -> sub.into("css"));
            task.from(extension.getUserManual().getRoot().dir("img"), sub -> {
                sub.include("**/*.png", "**/*.gif", "**/*.jpg", "**/*.svg");
                sub.into("img");
            });

            // Any extra resources and generated docinfo
            task.from(extension.getUserManual().getResources());
            task.from(generateDocinfo);

            // Write to working/usermanual/raw and expose that directory to other tasks via an extra property
            // TODO: This should be available on a Copy task.
            DirectoryProperty flattenedAsciidocDirectory = project.getObjects().directoryProperty();
            flattenedAsciidocDirectory.set(extension.getUserManual().getStagingRoot().dir("raw"));
            task.getOutputs().dir(flattenedAsciidocDirectory);
            task.getExtensions().getExtraProperties().set("destinationDirectory", flattenedAsciidocDirectory);
            task.into(flattenedAsciidocDirectory);
        });

        // Renders the single-page user manual (userguide_single.adoc → HTML)
        TaskProvider<AsciidoctorTask> userguideSinglePageHtml = tasks.register("userguideSinglePageHtml", AsciidoctorTask.class, task -> {
            task.setDescription("Generates HTML single-page user manual.");
            configureForUserGuideSinglePage(task, extension, project);
            task.outputOptions(options -> options.setBackends(singletonList("html5")));
            // TODO: This breaks the provider
            task.setOutputDir(extension.getUserManual().getStagingRoot().dir("render-single-html").get().getAsFile());
        });

        // Renders the multi-page user manual (filters some inputs not relevant to multi-page)
        TaskProvider<AsciidoctorTask> userguideMultiPage = tasks.register("userguideMultiPage", AsciidoctorTask.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generates multi-page user manual.");
            task.dependsOn(extension.getUserManual().getStagedDocumentation());

            task.sources(patternSet -> {
                patternSet.include("**/*.adoc");
                patternSet.exclude("javaProject*Layout.adoc");
                patternSet.exclude("userguide_single.adoc");
                patternSet.exclude("snippets/**/*.adoc");
            });

            // TODO: This breaks the provider
            task.setSourceDir(extension.getUserManual().getStagedDocumentation().get().getAsFile());
            // TODO: This breaks the provider
            task.setOutputDir(extension.getUserManual().getStagingRoot().dir("render-multi").get().getAsFile());

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("icons", "font");
            configureCodeHighlightingAttributes(attributes);
            attributes.put("toc", "auto");
            attributes.put("toclevels", 1);
            attributes.put("toc-title", "Contents");
            attributes.put("groovyDslPath", "../dsl");
            attributes.put("javadocPath", "../javadoc");
            attributes.put("kotlinDslPath", "../kotlin-dsl");
            // Used by SampleIncludeProcessor from `gradle/dotorg-docs`
            // TODO: This breaks the provider
            attributes.put("samples-dir", extension.getUserManual().getStagedDocumentation().get().getAsFile()); // TODO:
            task.attributes(attributes);
        });

        // Gathers only the rendered bits we care about into working/usermanual/final
        // (avoid overlapping outputs by copying exactly what we want from other intermediate tasks)
        TaskProvider<Sync> userguide = tasks.register("userguide", Sync.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Stages rendered user manual documentation.");

            task.from(userguideSinglePageHtml);
            task.from(userguideMultiPage);
            task.into(extension.getUserManual().getStagingRoot().dir("final"));

            // Copy images again so final layout mirrors deploy layout (avoids overlapping outputs)
            // TODO: Eliminate this duplication with the flatten task
            task.from(extension.getUserManual().getRoot().dir("img"), sub -> {
                sub.include("**/*.png", "**/*.gif", "**/*.jpg", "**/*.svg");
                sub.into("img");
            });
        });

        // Wire conventions/defaults for the userManual section of the extension
        extension.userManual(userManual -> {
            userManual.getRoot().convention(extension.getSourceRoot().dir("userguide"));
            userManual.getStagingRoot().convention(extension.getStagingRoot().dir("usermanual"));
            // TODO: These should be generated too
            userManual.getSnippets().convention(layout.getProjectDirectory().dir("src/snippets"));
            userManual.getSamples().convention(layout.getProjectDirectory().dir("src/samples"));

            // Expose the flattened "raw" Asciidoc directory to downstream tasks
            userManual.getStagedDocumentation().convention(userguideFlattenSources.flatMap(task -> (DirectoryProperty) task.getExtensions().getExtraProperties().get("destinationDirectory")));

            // Final rendered documentation directory (multi/single page) that stageDocs uses later
            userManual.getRenderedDocumentation().from(userguide);
        });
    }

    /** Common Asciidoctor attributes for code highlighting across tasks. */
    private static void configureCodeHighlightingAttributes(Map<String, Object> attributes) {
        attributes.put("source-highlighter", "highlight.js");
        //attributes.put("highlightjs-theme", "atom-one-dark");
        attributes.put("highlightjs-languages", "java,groovy,kotlin,toml,gradle,properties,text");
    }

    /** Configures the single-page user guide renderer (userguide_single.adoc). */
    private void configureForUserGuideSinglePage(AsciidoctorTask task, GradleDocumentationExtension extension, Project project) {
        task.setGroup("documentation");
        task.dependsOn(extension.getUserManual().getStagedDocumentation());
        // Skip in “quick” mode to speed up inner-loop
        task.onlyIf(t -> !extension.getQuickFeedback().get());

        task.sources(patternSet -> patternSet.include("userguide_single.adoc"));
        // TODO: This breaks the provider
        task.setSourceDir(extension.getUserManual().getStagedDocumentation().get().getAsFile());

        Map<String, Object> attributes = new HashMap<>();
        configureCodeHighlightingAttributes(attributes);
        attributes.put("toc", "macro");
        attributes.put("toclevels", 2);

        // Hard-wires versioned paths for cross-linking to other sections on docs.gradle.org
        // TODO: This breaks if version is changed later
        String versionUrl = DOCS_GRADLE_ORG + project.getVersion();
        attributes.put("groovyDslPath", versionUrl + "/dsl");
        attributes.put("javadocPath", versionUrl + "/javadoc");
        attributes.put("samplesPath", versionUrl + "/samples");
        attributes.put("kotlinDslPath", versionUrl + "/kotlin-dsl");
        // Used by SampleIncludeProcessor from `gradle/dotorg-docs`
        // TODO: This breaks the provider
        attributes.put("samples-dir", extension.getUserManual().getStagedDocumentation().get().getAsFile()); // TODO:
        task.attributes(attributes);
    }

    /** Registers the “broken internal links” check and wires it into `check`. */
    private void checkXrefLinksInUserManualAreValid(ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<FindBrokenInternalLinks> checkDeadInternalLinks = tasks.register("checkDeadInternalLinks", FindBrokenInternalLinks.class, task -> {
            task.getReportFile().convention(layout.getBuildDirectory().file("reports/dead-internal-links.txt"));
            task.getDocumentationRoot().convention(extension.getUserManual().getStagedDocumentation()); // working/usermanual/raw/
            task.getJavadocRoot().convention(layout.getBuildDirectory().dir("javadoc"));
            task.getReleaseNotesFile().convention(layout.getProjectDirectory().file("src/docs/release/notes.md"));
            task.getSamplesRoot().convention(layout.getBuildDirectory().dir("working/samples/docs"));
            task.dependsOn(tasks.named("javadocAll"));
            task.dependsOn(tasks.named("assembleSamples"));
        });

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(checkDeadInternalLinks));
    }

    /** Registers the “bad multi-language snippet pairs” check and wires it into `check`. */
    private void checkMultiLangSnippetsAreValid(ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<FindBadMultiLangSnippets> checkMultiLangSnippets = tasks.register("checkMultiLangSnippets", FindBadMultiLangSnippets.class, task -> {
            task.getDocumentationRoot().convention(extension.getUserManual().getStagedDocumentation()); // working/usermanual/raw/
        });

        // TO DO: tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(checkMultiLangSnippets));
    }

    /** Registers the “missing files referenced by the docs” check and wires it into `check`. */
    private void checkLinksInUserManualAreNotMissing(ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<FindMissingDocumentationFiles> checkMissingInternalLinks = tasks.register("checkMissingInternalLinks", FindMissingDocumentationFiles.class, task -> {
            task.getDocumentationRoot().convention(extension.getUserManual().getRoot());
            task.getJsonFilesDirectory().convention(layout.getProjectDirectory().dir("src/main/resources"));
        });

        // TO DO: tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(checkMissingInternalLinks));
    }
}
