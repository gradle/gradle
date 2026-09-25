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
import org.gradle.api.Task;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RelativePath;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

public class GradleUserManualPlugin implements Plugin<Project> {

    public static final String DOCS_GRADLE_ORG = "https://docs.gradle.org/";

    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        generateDefaultImports(project, tasks, extension);
        generateUserManual(project, tasks, layout, extension);

        checkXrefLinksInUserManualAreValid(layout, tasks, extension);
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

            // TODO: This doesn't allow adoc files to be generated?
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

        // Every page of the manual, except those that are only included by other pages
        Provider<PatternFilterable> multiPageSources = providers.of(UserManualPages.class, spec -> {
            spec.getParameters().getUserManualRoot().set(extension.getUserManual().getRoot());
            spec.getParameters().getExcludedFileNames().addAll("javaProject.*Layout\\.adoc", "userguide_single\\.adoc");
        }).map(pages -> new PatternSet().include(pages));

        TaskProvider<AsciidoctorTask> userguideSinglePageHtml = registerUserManualPublication(project, asciidoc, extension, "userguideSinglePage",
            GradleBuildDocumentationPlugin.FATAL_WARNINGS_SINGLE_PAGE,
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
        userguideSinglePageHtml.configure(task -> {
            task.setDescription("Generates HTML single-page user manual.");
            task.onlyIf(new NotQuickFeedback(extension.getQuickFeedback()));
            outputTo(task, extension.getUserManual().getStagingRoot().dir("render-single-html"));
        });

        TaskProvider<AsciidoctorTask> userguideMultiPage = registerUserManualPublication(project, asciidoc, extension, "userguideMultiPage",
            GradleBuildDocumentationPlugin.FATAL_WARNINGS,
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
        userguideMultiPage.configure(task -> {
            task.setDescription("Generates multi-page user manual.");
            task.setSourcePatterns(multiPageSources);
            outputTo(task, extension.getUserManual().getStagingRoot().dir("render-multi"));
        });

        return asList(userguideSinglePageHtml, userguideMultiPage);
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
        Pattern fatalWarnings,
        Action<AsciidoctorSourceSet> sources,
        Provider<Map<String, Object>> attributes
    ) {
        Provider<Directory> stagedDocumentation = extension.getUserManual().getStagedDocumentation();
        AsciidoctorPublication publication = asciidoc.getPublications().create(name, pub -> pub.sourceSet(sourceSet -> {
            sourceSet.setSourceDir(stagedDocumentation);
            // Includes in the top-level document and private docinfo files are resolved against the base dir
            sourceSet.baseDir(baseDir -> baseDir.baseDirFollowsSourceDir());
            sourceSet.fatalWarnings(fatalWarnings);
            sourceSet.attributes(attrs -> attrs.attributeProvider(attributes));
            sources.execute(sourceSet);
        }));
        publication.output(GradleBuildDocumentationPlugin.ASCIIDOCTORJ_TOOLCHAIN, GradleBuildDocumentationPlugin.HTML_FORMATTER);

        TaskProvider<AsciidoctorTask> task = project.getTasks().named(publication.taskNameFor(GradleBuildDocumentationPlugin.HTML_FORMATTER), AsciidoctorTask.class);
        task.configure(t -> {
            t.setGroup("documentation");
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
     * model5 derives the output directory from the publication name, under {@code build/docs}, which is where
     * {@code stageDocs} assembles all documentation. Keep rendering into the staging root, as before.
     */
    @SuppressWarnings("unchecked")
    private static void outputTo(AsciidoctorTask task, Provider<Directory> outputDir) {
        ((Property<Directory>) task.getOutputDir()).set(outputDir);
    }

    /**
     * Skips the single-page manual with {@code -PquickDocs}. Holds only a provider so it can be stored in the configuration cache.
     */
    private static final class NotQuickFeedback implements Spec<Task> {
        private final Provider<Boolean> quickFeedback;

        private NotQuickFeedback(Provider<Boolean> quickFeedback) {
            this.quickFeedback = quickFeedback;
        }

        @Override
        public boolean isSatisfiedBy(Task task) {
            return !quickFeedback.get();
        }
    }

    private static void stageUserManualImages(CopySpec spec, GradleDocumentationExtension extension) {
        spec.from(extension.getUserManual().getRoot().dir("img"), sub -> {
            sub.include("**/*.png", "**/*.gif", "**/*.jpg", "**/*.svg");
            sub.into("img");
        });
    }

    private void checkXrefLinksInUserManualAreValid(ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        TaskProvider<FindBrokenInternalLinks> checkDeadInternalLinks = tasks.register("checkDeadInternalLinks", FindBrokenInternalLinks.class, task -> {
            task.getReportFile().convention(layout.getBuildDirectory().file("reports/dead-internal-links.txt"));
            task.getDocumentationRoot().convention(extension.getUserManual().getStagedDocumentation()); // working/usermanual/raw/
            task.getJavadocRoot().convention(layout.getBuildDirectory().dir("javadoc"));
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
