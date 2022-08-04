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
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultGroovySourceSet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.GroovyRuntime;
import org.gradle.api.tasks.GroovySourceDirectorySet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.api.tasks.javadoc.GroovydocAccess;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.inject.Inject;
import java.util.function.BiFunction;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * Extends {@link org.gradle.api.plugins.JavaBasePlugin} to provide support for compiling and documenting Groovy
 * source files.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/groovy_plugin.html">Groovy plugin reference</a>
 */
public class GroovyBasePlugin implements Plugin<Project> {
    public static final String GROOVY_RUNTIME_EXTENSION_NAME = "groovyRuntime";

    private final ObjectFactory objectFactory;
    private final ModuleRegistry moduleRegistry;
    private final JvmPluginServices jvmPluginServices;

    private Project project;
    private GroovyRuntime groovyRuntime;

    @Inject
    public GroovyBasePlugin(
        ObjectFactory objectFactory,
        ModuleRegistry moduleRegistry,
        JvmEcosystemUtilities jvmPluginServices
    ) {
        this.objectFactory = objectFactory;
        this.moduleRegistry = moduleRegistry;
        this.jvmPluginServices = (JvmPluginServices) jvmPluginServices;
    }

    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().apply(JavaBasePlugin.class);

        configureGroovyRuntimeExtension();
        configureCompileDefaults();
        configureSourceSetDefaults();

        configureGroovydoc();
    }

    private void configureGroovyRuntimeExtension() {
        groovyRuntime = project.getExtensions().create(GROOVY_RUNTIME_EXTENSION_NAME, GroovyRuntime.class, project);
    }

    private void configureCompileDefaults() {
        project.getTasks().withType(GroovyCompile.class).configureEach(compile ->
            compile.getConventionMapping().map(
                "groovyClasspath",
                () -> groovyRuntime.inferGroovyClasspath(compile.getClasspath())
            )
        );
    }

    @SuppressWarnings("deprecation")
    private void configureSourceSetDefaults() {
        javaPluginExtension().getSourceSets().all(sourceSet -> {
            final DefaultGroovySourceSet groovySourceSet = new DefaultGroovySourceSet("groovy", ((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
            addSourceSetExtension(sourceSet, groovySourceSet);

            final SourceDirectorySet groovySource = groovySourceSet.getGroovy();
            groovySource.srcDir("src/" + sourceSet.getName() + "/groovy");

            // Explicitly capture only a FileCollection in the lambda below for compatibility with configuration-cache.
            @SuppressWarnings("UnnecessaryLocalVariable") final FileCollection groovySourceFiles = groovySource;
            sourceSet.getResources().getFilter().exclude(
                spec(element -> groovySourceFiles.contains(element.getFile()))
            );
            sourceSet.getAllJava().source(groovySource);
            sourceSet.getAllSource().source(groovySource);

            final TaskProvider<GroovyCompile> compileTask = project.getTasks().register(sourceSet.getCompileTaskName("groovy"), GroovyCompile.class, compile -> {
                JvmPluginsHelper.configureForSourceSet(sourceSet, groovySource, compile, compile.getOptions(), project);
                compile.setDescription("Compiles the " + sourceSet.getName() + " Groovy source.");
                compile.setSource(groovySource);
                compile.getJavaLauncher().convention(getToolchainTool(project, JavaToolchainService::launcherFor));
                compile.getGroovyOptions().getDisabledGlobalASTTransformations().convention(Sets.newHashSet("groovy.grape.GrabAnnotationTransformation"));
            });

            String compileClasspathConfigurationName = sourceSet.getCompileClasspathConfigurationName();
            JvmPluginsHelper.configureOutputDirectoryForSourceSet(sourceSet, groovySource, project, compileTask, compileTask.map(GroovyCompile::getOptions));
            useDefaultTargetPlatformInference(compileTask, compileClasspathConfigurationName);
            useDefaultTargetPlatformInference(compileTask, sourceSet.getRuntimeClasspathConfigurationName());

            // TODO: `classes` should be a little more tied to the classesDirs for a SourceSet so every plugin
            // doesn't need to do this.
            project.getTasks().named(sourceSet.getClassesTaskName(), task -> task.dependsOn(compileTask));

            // Explain that Groovy, for compile, also needs the resources (#9872)
            project.getConfigurations().getByName(compileClasspathConfigurationName).attributes(attrs ->
                attrs.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.getObjects().named(LibraryElements.class, LibraryElements.CLASSES_AND_RESOURCES)
                )
            );
        });
    }

    private void addSourceSetExtension(org.gradle.api.tasks.SourceSet sourceSet, DefaultGroovySourceSet groovySourceSet) {
        new DslObject(sourceSet).getConvention().getPlugins().put("groovy", groovySourceSet);
        sourceSet.getExtensions().add(GroovySourceDirectorySet.class, "groovy", groovySourceSet.getGroovy());
    }

    private void useDefaultTargetPlatformInference(TaskProvider<GroovyCompile> compileTask, String configurationName) {
        jvmPluginServices.useDefaultTargetPlatformInference(project.getConfigurations().getByName(configurationName), compileTask);
    }

    private void configureGroovydoc() {
        project.getTasks().withType(Groovydoc.class).configureEach(groovydoc -> {
            groovydoc.getConventionMapping().map("groovyClasspath", () -> {
                FileCollection groovyClasspath = groovyRuntime.inferGroovyClasspath(groovydoc.getClasspath());
                // Jansi is required to log errors when generating Groovydoc
                ConfigurableFileCollection jansi = project.getObjects().fileCollection().from(moduleRegistry.getExternalModule("jansi").getImplementationClasspath().getAsFiles());
                return groovyClasspath.plus(jansi);
            });
            groovydoc.getConventionMapping().map("destinationDir", () -> javaPluginExtension().getDocsDir().dir("groovydoc").get().getAsFile());
            groovydoc.getConventionMapping().map("docTitle", () -> projectExtension(ReportingExtension.class).getApiDocTitle());
            groovydoc.getConventionMapping().map("windowTitle", () -> projectExtension(ReportingExtension.class).getApiDocTitle());
            groovydoc.getAccess().convention(GroovydocAccess.PROTECTED);
            groovydoc.getIncludeAuthor().convention(false);
            groovydoc.getProcessScripts().convention(true);
            groovydoc.getIncludeMainForScripts().convention(true);
        });
    }

    private <T> Provider<T> getToolchainTool(Project project, BiFunction<JavaToolchainService, JavaToolchainSpec, Provider<T>> toolMapper) {
        final JavaPluginExtension extension = extensionOf(project, JavaPluginExtension.class);
        final JavaToolchainService service = extensionOf(project, JavaToolchainService.class);
        return toolMapper.apply(service, extension.getToolchain());
    }

    private JavaPluginExtension javaPluginExtension() {
        return projectExtension(JavaPluginExtension.class);
    }

    private <T> T projectExtension(Class<T> type) {
        return extensionOf(project, type);
    }

    private <T> T extensionOf(ExtensionAware extensionAware, Class<T> type) {
        return extensionAware.getExtensions().getByType(type);
    }
}
