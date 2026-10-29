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
import org.asciidoctor.gradle.model5.core.AsciidoctorModelExtension;
import org.asciidoctor.gradle.model5.core.publications.AsciidoctorPublication;
import org.asciidoctor.gradle.model5.core.publications.AsciidoctorSourceSet;
import org.asciidoctor.gradle.model5.core.tasks.AsciidoctorTask;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RelativePath;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

public class GradleUserManualPlugin implements Plugin<Project> {

    public static final String DOCS_GRADLE_ORG = "https://docs.gradle.org/";

    /**
     * Output alias for the multi-page rendering. Both renderings use the html formatter, and the
     * plugin keys a publication's log directory on toolchain and alias alone, so they need
     * distinct aliases to avoid writing to the same directory.
     */
    private static final String CHUNKED_HTML_OUTPUT = "chunkedHtml";
    private static final String MARKDOWN_OUTPUT = "markdown";

    /**
     * Agents tend to truncate or summarize pages above this size, which loses the details the Markdown copy is for.
     */
    private static final long MARKDOWN_PAGE_SIZE_WARNING_THRESHOLD = 100 * 1024;

    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        generateDefaultImports(project, tasks, extension);
        generateUserManual(project, tasks, layout, extension);

        checkXrefLinksInUserManualAreValid(layout, tasks, project.getObjects(), extension);
    }

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

        Provider<Directory> generatedDirectory = extension.getUserManual().getStagingRoot().dir("generated");

        TaskProvider<GenerateApiMapping> apiMapping = tasks.register("apiMapping", GenerateApiMapping.class, task -> {
            task.getMetaDataFile().convention(extension.getDslReference().getGeneratedMetaDataFile());
            task.getMappingDestFile().convention(generatedDirectory.map(dir -> dir.file("api-mapping.txt")));
            task.getExcludedPackages().convention(excludedPackages);
        });
        TaskProvider<GenerateDefaultImports> defaultImports = tasks.register("defaultImports", GenerateDefaultImports.class, task -> {
            task.getMetaDataFile().convention(extension.getDslReference().getGeneratedMetaDataFile());
            task.getImportsDestFile().convention(generatedDirectory.map(dir -> dir.file("default-imports.txt")));
            task.getExcludedPackages().convention(excludedPackages);
        });
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main", main ->
            main.getOutput().dir(singletonMap("builtBy", asList(apiMapping, defaultImports)), generatedDirectory)
        );

        extension.getUserManual().getResources().from(apiMapping);
        extension.getUserManual().getResources().from(defaultImports);
    }

    private void generateUserManual(Project project, TaskContainer tasks, ProjectLayout layout, GradleDocumentationExtension extension) {
        TaskProvider<GenerateDocInfo> generateDocinfo = tasks.register("generateDocInfo", GenerateDocInfo.class, task -> {
            task.getDocumentationFiles().from(extension.getUserManual().getRoot());
            task.getDocumentationRoot().convention(extension.getUserManual().getRoot());
            task.getDestinationDirectory().convention(layout.getBuildDirectory().dir("tmp/" + task.getName()));
        });

        TaskProvider<StageUserManualSources> userguideFlattenSources = tasks.register("stageUserguideSource", StageUserManualSources.class, task -> {
            task.setDuplicatesStrategy(DuplicatesStrategy.FAIL);

            // UserManualPages lists the multi-page pages by walking this source root rather than the staged directory
            task.from(extension.getUserManual().getRoot(), sub -> {
                sub.include("**/*.adoc");
                // Flatten adocs into a single directory
                sub.eachFile(fcd -> fcd.setRelativePath(RelativePath.parse(true, fcd.getName())));
            });

            // From the snippets, filter out files generated if the build contained was ever executed
            task.from(extension.getUserManual().getSnippets(), sub -> {
                sub.into("snippets");
                sub.exclude("**/.gradle/**");
                sub.exclude("**/build/**");
                sub.setIncludeEmptyDirs(false);
            });
            task.from(extension.getCssFiles(), sub -> sub.into("css"));
            stageUserManualImages(task, extension);
            task.from(extension.getUserManual().getResources());

            task.from(generateDocinfo);

            task.getStagingDirectory().set(extension.getUserManual().getStagingRoot().dir("raw"));
            task.into(task.getStagingDirectory());
        });

        List<TaskProvider<AsciidoctorTask>> renders = registerUserManualRenderings(project, extension);
        TaskProvider<Sync> userguide = registerUserguide(tasks, extension, task -> renders.forEach(render -> {
            task.dependsOn(render);
            task.from(render.flatMap(AsciidoctorTask::getOutputDir));
        }));

        extension.userManual(userManual -> {
            userManual.getRoot().convention(extension.getSourceRoot().dir("userguide"));
            userManual.getStagingRoot().convention(extension.getStagingRoot().dir("usermanual"));
            // TODO: These should be generated too
            userManual.getSnippets().convention(layout.getProjectDirectory().dir("src/snippets"));
            userManual.getStagedDocumentation().convention(userguideFlattenSources.flatMap(StageUserManualSources::getStagingDirectory));
            userManual.getRenderedDocumentation().from(userguide);
        });
    }

    /**
     * Registers the single-page and multi-page renderings of the staged user manual.
     */
    private static List<TaskProvider<AsciidoctorTask>> registerUserManualRenderings(Project project, GradleDocumentationExtension extension) {
        AsciidoctorModelExtension asciidoc = project.getExtensions().getByType(AsciidoctorModelExtension.class);
        ProviderFactory providers = project.getProviders();
        Provider<Directory> stagedDocumentation = extension.getUserManual().getStagedDocumentation();
        Provider<Boolean> quickFeedback = extension.getQuickFeedback();

        // Every page of the manual, except those that are only included by other pages
        Provider<PatternFilterable> multiPageSources = providers.of(UserManualPages.class, spec -> {
            spec.getParameters().getUserManualRoot().set(extension.getUserManual().getRoot());
            spec.getParameters().getExcludedFileNames().addAll("javaProject.*Layout\\.adoc", "userguide_single\\.adoc");
        }).map(pages -> new PatternSet().include(pages));

        TaskProvider<AsciidoctorTask> asciidoctorUserguideSinglePageHtml = registerUserManualPublication(project, asciidoc, extension, "userguideSinglePage", GradleBuildDocumentationPlugin.HTML_FORMATTER,
            sourceSet -> sourceSet.sources("userguide_single.adoc"),
            providers.provider(() -> {
                Map<String, Object> attributes = commonAttributes(extension);
                attributes.put("toc", "macro");
                attributes.put("toclevels", 2);
                String gradleVersion = extension.getGradleVersion().get();
                attributes.put("groovyDslPath", DOCS_GRADLE_ORG + gradleVersion + "/dsl");
                attributes.put("javadocPath", DOCS_GRADLE_ORG + gradleVersion + "/javadoc");
                attributes.put("kotlinDslPath", DOCS_GRADLE_ORG + gradleVersion + "/kotlin-dsl");
                // Used by SampleIncludeProcessor from `gradle/dotorg-docs`
                attributes.put("samples-dir", stagedDocumentation.get().getAsFile().getAbsolutePath());
                return attributes;
            }));
        asciidoctorUserguideSinglePageHtml.configure(task -> {
            task.setDescription("Generates HTML single-page user manual.");
            task.onlyIf("quick feedback mode is not enabled", t -> !quickFeedback.get());
            outputTo(task, extension.getUserManual().getStagingRoot().dir("render-single-html"));
        });

        TaskProvider<AsciidoctorTask> asciidoctorUserguideMultiPageChunkedHtml = registerUserManualPublication(project, asciidoc, extension, "userguideMultiPage", CHUNKED_HTML_OUTPUT,
            sourceSet -> { },
            providers.provider(() -> {
                Map<String, Object> attributes = commonAttributes(extension);
                attributes.put("icons", "font");
                attributes.put("toc", "auto");
                attributes.put("toclevels", 2);
                attributes.put("toc-title", "On this Page");
                attributes.put("groovyDslPath", "../dsl");
                attributes.put("javadocPath", "../javadoc");
                attributes.put("kotlinDslPath", "../kotlin-dsl");
                // Used by SampleIncludeProcessor from `gradle/dotorg-docs`
                attributes.put("samples-dir", stagedDocumentation.get().getAsFile().getAbsolutePath());
                return attributes;
            }));
        asciidoctorUserguideMultiPageChunkedHtml.configure(task -> {
            task.setDescription("Generates multi-page user manual.");
            task.setSourcePatterns(multiPageSources);
            outputTo(task, extension.getUserManual().getStagingRoot().dir("render-multi"));
        });

        // The Markdown rendering is the AI-readable copy of the manual: one .md per page, served next to
        // the HTML. Redirect stubs carry no prose, so they are left out.
        Provider<PatternFilterable> markdownSources = providers.of(UserManualPages.class, spec -> {
            spec.getParameters().getUserManualRoot().set(extension.getUserManual().getRoot());
            spec.getParameters().getExcludedFileNames().addAll("javaProject.*Layout\\.adoc", "userguide_single\\.adoc");
            spec.getParameters().getExcludedDirectories().add("redirects");
        }).map(pages -> new PatternSet().include(pages));

        TaskProvider<AsciidoctorTask> asciidoctorUserguideMultiPageMarkdown = registerUserManualOutput(project, asciidoc, extension, "userguideMultiPage",
            GradleBuildDocumentationPlugin.MARKDOWN_FORMATTER, MARKDOWN_OUTPUT);
        asciidoctorUserguideMultiPageMarkdown.configure(task -> {
            task.setDescription("Generates the multi-page user manual as Markdown, one .md file per page.");
            task.setSourcePatterns(markdownSources);
            task.onlyIf("quick feedback mode is not enabled", t -> !quickFeedback.get());
            outputTo(task, extension.getUserManual().getStagingRoot().dir("render-markdown"));
            Provider<Directory> markdownDir = task.getOutputDir();
            task.doLast(t -> warnAboutLargeMarkdownPages(t.getLogger(), markdownDir.get().getAsFile().toPath()));
        });

        // The plugin derives the conversion task names from the publication and the output alias, which is
        // not what contributors have always typed. These keep the historical names working as entry points.
        registerRenderingAlias(project, "userguideSinglePageHtml", "Generates HTML single-page user manual.", asciidoctorUserguideSinglePageHtml);
        registerRenderingAlias(project, "userguideMultiPage", "Generates multi-page user manual.", asciidoctorUserguideMultiPageChunkedHtml);

        return asList(asciidoctorUserguideSinglePageHtml, asciidoctorUserguideMultiPageChunkedHtml, asciidoctorUserguideMultiPageMarkdown);
    }

    private static void warnAboutLargeMarkdownPages(Logger logger, Path markdownDir) {
        try (Stream<Path> files = Files.walk(markdownDir)) {
            List<Path> pages = files.filter(file -> file.toString().endsWith(".md")).collect(Collectors.toList());
            for (Path page : pages) {
                long size = Files.size(page);
                if (size > MARKDOWN_PAGE_SIZE_WARNING_THRESHOLD) {
                    logger.warn("Markdown page {} is {} KB, larger than the {} KB that agents read reliably.",
                        markdownDir.relativize(page), size / 1024, MARKDOWN_PAGE_SIZE_WARNING_THRESHOLD / 1024);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Registers a lifecycle task that runs {@code rendering}, under the name the build used before the
     * user manual moved to the Asciidoctor Gradle plugin's model5 DSL.
     */
    private static void registerRenderingAlias(Project project, String name, String description, TaskProvider<AsciidoctorTask> rendering) {
        project.getTasks().register(name, task -> {
            task.setGroup("documentation");
            task.setDescription(description);
            task.dependsOn(rendering);
        });
    }

    /**
     * Copies the rendered user manual, its images and scripts into one directory.
     * Avoids overlapping outputs by copying exactly what we want from other intermediate tasks.
     */
    private static TaskProvider<Sync> registerUserguide(TaskContainer tasks, GradleDocumentationExtension extension, Action<Sync> renderings) {
        return tasks.register("userguide", Sync.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Stages rendered user manual documentation.");

            renderings.execute(task);
            task.into(extension.getUserManual().getStagingRoot().dir("final"));
            stageUserManualImages(task, extension);
            task.from(extension.getUserManual().getRoot().dir("js"), sub -> {
                sub.include("**/*.js");
                sub.into("js");
            });
        });
    }

    /**
     * Registers a model5 publication that renders the staged user manual to HTML with the
     * {@link GradleBuildDocumentationPlugin#ASCIIDOCTORJ_TOOLCHAIN} toolchain, and returns its conversion task.
     */
    private static TaskProvider<AsciidoctorTask> registerUserManualPublication(
        Project project,
        AsciidoctorModelExtension asciidoc,
        GradleDocumentationExtension extension,
        String name,
        String outputAlias,
        Action<AsciidoctorSourceSet> sources,
        Provider<Map<String, Object>> attributes
    ) {
        Provider<Directory> stagedDocumentation = extension.getUserManual().getStagedDocumentation();
        AsciidoctorPublication publication = asciidoc.getPublications().create(name, pub -> pub.sourceSet(sourceSet -> {
            sourceSet.setSourceDir(stagedDocumentation);
            // Includes in the top-level document and private docinfo files are resolved against the base dir
            sourceSet.baseDir(baseDir -> baseDir.baseDirFollowsSourceDir());
            sourceSet.fatalWarnings(GradleBuildDocumentationPlugin.ANY_WARNING);
            sourceSet.attributes(attrs -> attrs.attributeProvider(attributes));
            sources.execute(sourceSet);
        }));
        return registerUserManualOutput(project, asciidoc, extension, name, GradleBuildDocumentationPlugin.HTML_FORMATTER, outputAlias);
    }

    /**
     * Adds an output of the {@link GradleBuildDocumentationPlugin#ASCIIDOCTORJ_TOOLCHAIN} toolchain to a user manual
     * publication, and returns its conversion task. A publication can carry several outputs, one per format.
     */
    private static TaskProvider<AsciidoctorTask> registerUserManualOutput(
        Project project,
        AsciidoctorModelExtension asciidoc,
        GradleDocumentationExtension extension,
        String publicationName,
        String formatter,
        String outputAlias
    ) {
        Provider<Directory> stagedDocumentation = extension.getUserManual().getStagedDocumentation();
        AsciidoctorPublication publication = asciidoc.getPublications().getByName(publicationName);
        // The alias names the output, and the plugin derives both the conversion task name and the
        // per-run log directory (reports/asciidoc/logs/<toolchain>/<alias>) from it.
        publication.output(GradleBuildDocumentationPlugin.ASCIIDOCTORJ_TOOLCHAIN, formatter, outputAlias);

        TaskProvider<AsciidoctorTask> task = project.getTasks().named(publication.taskNameFor(outputAlias), AsciidoctorTask.class);
        task.configure(t -> {
            // Deliberately not in the documentation group: the derived name is an implementation detail
            // of the plugin, and registerRenderingAlias lists the name contributors use instead.
            t.dependsOn(stagedDocumentation);

            // TODO: Break the paths assumed here
            TaskInputs inputs = t.getInputs();
            inputs.files(extension.getCssFiles())
                .withPropertyName("manual")
                .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir("src/main/resources")
                .withPropertyName("resources")
                .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir(extension.getUserManual().getSnippets())
                .withPropertyName("snippets")
                .withPathSensitivity(PathSensitivity.RELATIVE);
            inputs.dir(stagedDocumentation.map(dir -> dir.dir("css")))
                .withPropertyName("stylesdir")
                .withPathSensitivity(PathSensitivity.RELATIVE);
        });
        return task;
    }

    private static Map<String, Object> commonAttributes(GradleDocumentationExtension extension) {
        // TODO: Break the paths assumed here
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("stylesdir", extension.getUserManual().getStagedDocumentation().get().dir("css").getAsFile().getAbsolutePath());
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
        attributes.put("javaApi", extension.getJavadocs().getJavaApi().get().toString());
        attributes.put("jdkDownloadUrl", "https://jdk.java.net/");
        attributes.put("javadocReferenceUrl", extension.getJavadocs().getJavadocReferenceUrl().get().toString());
        attributes.put("minJdkVersion", extension.getJavadocs().getMinJdkVersion().get().toString());

        attributes.put("antManual", "https://ant.apache.org/manual");
        attributes.put("docsUrl", "https://docs.gradle.org");

        attributes.put("gradleVersion", extension.getGradleVersion().get());
        attributes.put("gradleVersion8", extension.getGradleVersion8().get());
        attributes.put("snippetsPath", "snippets");
        return attributes;
    }

    /**
     * Redirects a rendering into the user manual's staging root.
     * <p>
     * The plugin hardcodes its output under {@code build/docs/asciidoc<Publication>/<alias>}, inside
     * {@code build/docs}, the directory {@code stageDocs} syncs the assembled documentation into. Rendering
     * there would nest one task's output inside another's, so each rendering is redirected under
     * {@code build/working/usermanual/}. Consumers read the location back off the task, so only the task
     * itself needs to know where it writes.
     */
    @SuppressWarnings("unchecked")
    private static void outputTo(AsciidoctorTask task, Provider<Directory> outputDir) {
        Provider<Directory> destination = task.getOutputDir();
        if (!(destination instanceof Property)) {
            throw new IllegalStateException("Cannot redirect the output of " + task.getPath() + ": "
                + "AsciidoctorTask.getOutputDir() returned " + destination.getClass().getName() + ", which is not a Property. "
                + "The Asciidoctor Gradle plugin no longer allows the output directory to be overridden this way.");
        }
        ((Property<Directory>) destination).set(outputDir);
    }

    private static void stageUserManualImages(CopySpec spec, GradleDocumentationExtension extension) {
        spec.from(extension.getUserManual().getRoot().dir("img"), sub -> {
            sub.include("**/*.png", "**/*.gif", "**/*.jpg", "**/*.svg");
            sub.into("img");
        });
    }

    private void checkXrefLinksInUserManualAreValid(ProjectLayout layout, TaskContainer tasks, ObjectFactory objects, GradleDocumentationExtension extension) {
        Provider<Directory> dslHtmlDirectory = tasks.named("dslHtml", Docbook2Xhtml.class).flatMap(Docbook2Xhtml::getDestinationDirectory);
        TaskProvider<FindBrokenInternalLinks> checkDeadInternalLinks = tasks.register("checkDeadInternalLinks", FindBrokenInternalLinks.class, task -> {
            task.getReportFile().convention(layout.getBuildDirectory().file("reports/dead-internal-links.txt"));
            task.getDocumentationRoot().convention(extension.getUserManual().getStagedDocumentation()); // working/usermanual/raw/
            task.getJavadocRoot().convention(layout.getBuildDirectory().dir("javadoc"));
            // The DSL reference is not generated in quick feedback mode
            task.getDslRoot().convention(extension.getQuickFeedback().flatMap(quick -> quick ? objects.directoryProperty() : dslHtmlDirectory));
            task.getReleaseNotesFile().convention(layout.getProjectDirectory().file("src/docs/release/notes.md"));
            task.dependsOn(tasks.named("javadocAll"));
        });

        tasks.register("checkDeadExternalLinks", FindBrokenExternalLinks.class, task -> {
            task.setGroup("verification");
            task.setDescription("Checks external HTTP(S) links in .adoc and .md documentation files.");
            task.getReportFile().convention(layout.getBuildDirectory().file("reports/dead-external-links.txt"));
            task.getDocumentationRoot().convention(layout.getProjectDirectory().dir("src/docs"));
        });

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(checkDeadInternalLinks));
    }
}
