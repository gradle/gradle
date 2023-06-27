/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins;

import com.google.common.collect.Sets;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.plugins.jvm.internal.JvmLanguageUtilities;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.GroovyRuntime;
import org.gradle.api.tasks.GroovySourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.api.tasks.javadoc.GroovydocAccess;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * Extends {@link org.gradle.api.plugins.JavaBasePlugin} to provide support for compiling and documenting Groovy
 * source files.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/groovy_plugin.html">Groovy plugin reference</a>
 */
public abstract class GroovyBasePlugin implements Plugin<Project> {
    public static final String GROOVY_RUNTIME_EXTENSION_NAME = "groovyRuntime";

    private final ObjectFactory objectFactory;
    private final ModuleRegistry moduleRegistry;
    private final JvmLanguageUtilities jvmEcosystemUtilities;

    @Inject
    public GroovyBasePlugin(
        ObjectFactory objectFactory,
        ModuleRegistry moduleRegistry,
        JvmLanguageUtilities jvmPluginServices
    ) {
        this.objectFactory = objectFactory;
        this.moduleRegistry = moduleRegistry;
        this.jvmEcosystemUtilities = jvmPluginServices;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        GroovyRuntime groovyRuntime = project.getExtensions().create(GROOVY_RUNTIME_EXTENSION_NAME, GroovyRuntime.class, project);

        configureCompileDefaults(project, groovyRuntime);
        configureSourceSetDefaults(project);
        configureGroovydoc(project, groovyRuntime);
    }

    private static void configureCompileDefaults(Project project, GroovyRuntime groovyRuntime) {
        project.getTasks().withType(GroovyCompile.class).configureEach(compile ->
            compile.getConventionMapping().map(
                "groovyClasspath",
                () -> groovyRuntime.inferGroovyClasspath(compile.getClasspath())
            )
        );
    }

    private void configureSourceSetDefaults(Project project) {
        javaPluginExtension(project).getSourceSets().all(sourceSet -> {

            GroovySourceDirectorySet groovySource = getGroovySourceDirectorySet(sourceSet);
            sourceSet.getExtensions().add(GroovySourceDirectorySet.class, "groovy", groovySource);
            groovySource.srcDir("src/" + sourceSet.getName() + "/groovy");

            // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
            final FileCollection groovySourceFiles = groovySource;
            sourceSet.getResources().getFilter().exclude(
                spec(element -> groovySourceFiles.contains(element.getFile()))
            );
            sourceSet.getAllJava().source(groovySource);
            sourceSet.getAllSource().source(groovySource);

            TaskProvider<GroovyCompile> compileTask = createGroovyCompileTask(project, sourceSet, groovySource);

            ConfigurationContainer configurations = project.getConfigurations();
            configureLibraryElements(sourceSet, configurations, project.getObjects());
            configureTargetPlatform(compileTask, sourceSet, configurations);
        });
    }

    /**
     * In 9.0, once {@link org.gradle.api.internal.tasks.DefaultGroovySourceSet} is removed, we can update this to only construct the source directory
     * set instead of the entire source set.
     */
    @SuppressWarnings("deprecation")
    private GroovySourceDirectorySet getGroovySourceDirectorySet(SourceSet sourceSet) {
        final org.gradle.api.internal.tasks.DefaultGroovySourceSet groovySourceSet = objectFactory.newInstance(org.gradle.api.internal.tasks.DefaultGroovySourceSet.class, "groovy", ((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
        DeprecationLogger.whileDisabled(() ->
            new DslObject(sourceSet).getConvention().getPlugins().put("groovy", groovySourceSet)
        );
        return groovySourceSet.getGroovy();
    }

    private static void configureLibraryElements(SourceSet sourceSet, ConfigurationContainer configurations, ObjectFactory objectFactory) {
        // Explain that Groovy, for compile, also needs the resources (#9872)
        configurations.getByName(sourceSet.getCompileClasspathConfigurationName()).attributes(attrs ->
            attrs.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objectFactory.named(LibraryElements.class, LibraryElements.CLASSES_AND_RESOURCES)
            )
        );
    }

    private void configureTargetPlatform(TaskProvider<GroovyCompile> compileTask, SourceSet sourceSet, ConfigurationContainer configurations) {
        jvmEcosystemUtilities.useDefaultTargetPlatformInference(configurations.getByName(sourceSet.getCompileClasspathConfigurationName()), compileTask);
        jvmEcosystemUtilities.useDefaultTargetPlatformInference(configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()), compileTask);
    }

    private TaskProvider<GroovyCompile> createGroovyCompileTask(Project project, SourceSet sourceSet, GroovySourceDirectorySet groovySource) {
        final TaskProvider<GroovyCompile> compileTask = project.getTasks().register(sourceSet.getCompileTaskName("groovy"), GroovyCompile.class, groovyCompile -> {
            JvmPluginsHelper.compileAgainstJavaOutputs(groovyCompile, sourceSet, objectFactory);
            JvmPluginsHelper.configureAnnotationProcessorPath(sourceSet, groovySource, groovyCompile.getOptions(), project);
            groovyCompile.setDescription("Compiles the " + groovySource + ".");
            groovyCompile.setSource(groovySource);
            groovyCompile.getJavaLauncher().convention(getJavaLauncher(project));

            groovyCompile.getGroovyOptions().getDisabledGlobalASTTransformations().convention(Sets.newHashSet("groovy.grape.GrabAnnotationTransformation"));
        });
        JvmPluginsHelper.configureOutputDirectoryForSourceSet(sourceSet, groovySource, project, compileTask, compileTask.map(GroovyCompile::getOptions));

        // TODO: `classes` should be a little more tied to the classesDirs for a SourceSet so every plugin
        // doesn't need to do this.
        project.getTasks().named(sourceSet.getClassesTaskName(), task -> task.dependsOn(compileTask));

        return compileTask;
    }

    private void configureGroovydoc(Project project, GroovyRuntime groovyRuntime) {
        project.getTasks().withType(Groovydoc.class).configureEach(groovydoc -> {
            groovydoc.getConventionMapping().map("groovyClasspath", () -> {
                FileCollection groovyClasspath = groovyRuntime.inferGroovyClasspath(groovydoc.getClasspath());
                // Jansi is required to log errors when generating Groovydoc
                ConfigurableFileCollection jansi = project.getObjects().fileCollection().from(moduleRegistry.getExternalModule("jansi").getImplementationClasspath().getAsFiles());
                return groovyClasspath.plus(jansi);
            });
            groovydoc.getConventionMapping().map("destinationDir", () -> javaPluginExtension(project).getDocsDir().dir("groovydoc").get().getAsFile());
            groovydoc.getConventionMapping().map("docTitle", () -> extensionOf(project, ReportingExtension.class).getApiDocTitle());
            groovydoc.getConventionMapping().map("windowTitle", () -> extensionOf(project, ReportingExtension.class).getApiDocTitle());
            groovydoc.getAccess().convention(GroovydocAccess.PROTECTED);
            groovydoc.getIncludeAuthor().convention(false);
            groovydoc.getProcessScripts().convention(true);
            groovydoc.getIncludeMainForScripts().convention(true);
        });
    }

    private static Provider<JavaLauncher> getJavaLauncher(Project project) {
        final JavaPluginExtension extension = javaPluginExtension(project);
        final JavaToolchainService service = extensionOf(project, JavaToolchainService.class);
        return service.launcherFor(extension.getToolchain());
    }

    private static JavaPluginExtension javaPluginExtension(Project project) {
        return extensionOf(project, JavaPluginExtension.class);
    }

    private static <T> T extensionOf(ExtensionAware extensionAware, Class<T> type) {
        return extensionAware.getExtensions().getByType(type);
    }
}
