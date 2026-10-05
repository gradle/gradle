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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gradlebuild.basics.BuildEnvironmentKt;
import gradlebuild.basics.PublicApi;
import gradlebuild.basics.PublicKotlinDslApi;
import org.asciidoctor.gradle.model5.core.AsciidoctorModelExtension;
import org.asciidoctor.gradle.model5.jvm.JvmModel;
import org.asciidoctor.gradle.model5.jvm.extensions.AsciidoctorjGenericExtension;
import org.asciidoctor.gradle.model5.jvm.formatters.AsciidoctorjHtml5;
import org.asciidoctor.gradle.model5.jvm.plugins.AsciidoctorjBasePlugin;
import org.asciidoctor.gradle.model5.jvm.toolchains.AsciidoctorjToolchain;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public abstract class GradleBuildDocumentationPlugin implements Plugin<Project> {

    /**
     * The AsciidoctorJ toolchain that renders Gradle's documentation.
     */
    public static final String ASCIIDOCTORJ_TOOLCHAIN = "asciidoctorj";

    /**
     * The HTML output formatter registered on {@link #ASCIIDOCTORJ_TOOLCHAIN}.
     */
    public static final String HTML_FORMATTER = "html";

    /**
     * Any Asciidoctor warning fails the multi-page user manual.
     */
    public static final Pattern FATAL_WARNINGS = Pattern.compile(".*");

    /**
     * Like {@link #FATAL_WARNINGS}, but for the single-page user manual, which combines pages that are written and
     * linked to as standalone pages. Their IDs can clash there (e.g. {@code task_dependencies} in the upgrading guides for
     * Gradle 7 and 9, which deprecation messages link to), so duplicate IDs are only fatal in the multi-page manual.
     */
    public static final Pattern FATAL_WARNINGS_SINGLE_PAGE = Pattern.compile("^(?!id assigned to .+ already in use: ).*");

    private static final String GRADLE_EXTENSIONS = "gradleDocs";

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
        extension.getGradleVersion().convention(project.provider(() -> project.getVersion().toString()));
        extension.getGradleVersion8().convention(
            getProviders().fileContents(BuildEnvironmentKt.releasedVersionsFile(project))
                .getAsText()
                .map(GradleBuildDocumentationPlugin::findLatestGradle8Version)
        );

        // The user manual plugin renders through this toolchain
        configureAsciidoctorJ(project);

        project.apply(target -> target.plugin(GradleReleaseNotesPlugin.class));
        project.apply(target -> target.plugin(GradleJavadocsPlugin.class));
        project.apply(target -> target.plugin(GradleKotlinDslReferencePlugin.class));
        project.apply(target -> target.plugin(GradleDslReferencePlugin.class));
        project.apply(target -> target.plugin(GradleUserManualPlugin.class));

        addUtilityTasks(project, tasks, extension);

        checkDocumentation(tasks, extension);
    }

    private static String findLatestGradle8Version(String releasedVersionsJson) {
        JsonObject root = JsonParser.parseString(releasedVersionsJson).getAsJsonObject();
        for (var element : root.getAsJsonArray("finalReleases")) {
            String version = element.getAsJsonObject().get("version").getAsString();
            if (version.startsWith("8.")) {
                return version;
            }
        }
        throw new IllegalStateException("No 8.x release found in released-versions.json");
    }

    /**
     * Sets up the AsciidoctorJ toolchain ({@link #ASCIIDOCTORJ_TOOLCHAIN}) of the Asciidoctor Gradle plugin's model5 DSL,
     * with Gradle's own Asciidoctor extensions and an HTML output formatter ({@link #HTML_FORMATTER}).
     */
    private static void configureAsciidoctorJ(Project project) {
        project.getPluginManager().apply(AsciidoctorjBasePlugin.class);
        VersionCatalog buildLibs = project.getExtensions().getByType(VersionCatalogsExtension.class).named("buildLibs");
        AsciidoctorModelExtension asciidoc = project.getExtensions().getByType(AsciidoctorModelExtension.class);

        AsciidoctorjToolchain toolchain = asciidoc.getToolchains().create(ASCIIDOCTORJ_TOOLCHAIN, AsciidoctorjToolchain.class);
        toolchain.useAsciidoctorj(buildLibs.findVersion("asciidoctor").get().getRequiredVersion());
        // head.html, header.html and footer.html, read from the classpath by HeaderInjectingPostprocessor
        toolchain.classpath(project.files("src/main/resources"));

        // Render in a worker process, as the classic plugin did with ExecutionMode.OUT_OF_PROCESS
        toolchain.getRegisteredOutputFormatters().register(HTML_FORMATTER, AsciidoctorjHtml5.class,
            html -> html.useProcessIsolation(forkOptions -> { }));

        toolchain.getAsciidocExtensions().register(GRADLE_EXTENSIONS, AsciidoctorjGenericExtension.class, extension -> {
            // Added lazily, so that applying this plugin doesn't require the project to exist, e.g. in build-logic tests
            project.getConfigurations()
                .getByName(JvmModel.nameForExtensionConfiguration(ASCIIDOCTORJ_TOOLCHAIN, GRADLE_EXTENSIONS))
                .getDependencies()
                .addLater(project.provider(() -> project.getDependencies().project(Collections.singletonMap("path", ":docs-asciidoctor-extensions"))));
            // The extensions declare an API dependency on AsciidoctorJ. model5 resolves extensions separately from the
            // engine, so keep AsciidoctorJ and JRuby coming only from the toolchain to avoid two versions on the classpath.
            project.getConfigurations()
                .getByName(JvmModel.nameForExtensionConfigurationResolvable(ASCIIDOCTORJ_TOOLCHAIN, GRADLE_EXTENSIONS))
                .exclude(Collections.singletonMap("group", "org.asciidoctor"));
        });
    }

    private void applyConventions(Project project, TaskContainer tasks, ObjectFactory objects, ProjectLayout layout, GradleDocumentationExtension extension) {

        TaskProvider<Sync> stageDocs = tasks.register("stageDocs", Sync.class, task -> {
            // release notes goes in the root of the docs
            task.from(extension.getReleaseNotes().getRenderedDocumentation());

            // release notes assets go into $root/$assetsName/
            task.from(extension.getReleaseNotes().getReleaseNotesAssets(), sub -> sub.into(extension.getReleaseNotes().getReleaseNotesAssets().map(dir -> dir.getAsFile().getName())));

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

    private void addUtilityTasks(Project project, TaskContainer tasks, GradleDocumentationExtension extension) {
        JavaToolchainService javaToolchains = project.getExtensions().getByType(JavaToolchainService.class);
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);

        tasks.register("serveDocs", ServeDocs.class, task -> {
            task.setDescription("Runs a local webserver to serve generated documentation.");
            task.setGroup("documentation");

            int webserverPort = 8000;
            task.getDocsDirectory().convention(extension.getDocumentationRenderedRoot());
            task.getPort().convention(webserverPort);
            task.getJavaLauncher().convention(javaToolchains.launcherFor(javaPluginExtension.getToolchain()));

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
            task.getJvmArgumentProviders().add((CommandLineArgumentProvider) () -> List.of(
                "-Dorg.gradle.docs.releasenotes.rendered=" + extension.getReleaseNotes().getRenderedDocumentation().get().getAsFile()
            ));
        });
    }
}
