/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.JavaEcosystemSupport;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.DefaultJavaPluginConvention;
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension;
import org.gradle.api.plugins.internal.SourceSetUtil;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.jvm.tasks.ProcessResources;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;

/**
 * <p>A {@link org.gradle.api.Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 */
public class JavaBasePlugin implements Plugin<ProjectInternal> {
    public static final String CHECK_TASK_NAME = LifecycleBasePlugin.CHECK_TASK_NAME;

    public static final String VERIFICATION_GROUP = LifecycleBasePlugin.VERIFICATION_GROUP;
    public static final String BUILD_TASK_NAME = LifecycleBasePlugin.BUILD_TASK_NAME;
    public static final String BUILD_DEPENDENTS_TASK_NAME = "buildDependents";
    public static final String BUILD_NEEDED_TASK_NAME = "buildNeeded";
    public static final String DOCUMENTATION_GROUP = "documentation";

    /**
     * A list of known artifact types which are known to prevent from
     * publication.
     *
     * @since 5.3
     */
    @Incubating
    public static final Set<String> UNPUBLISHABLE_VARIANT_ARTIFACTS = ImmutableSet.of(
            ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
            ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY,
            ArtifactTypeDefinition.DIRECTORY_TYPE
    );

    private final ObjectFactory objectFactory;

    @Inject
    public JavaBasePlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(BasePlugin.class);
        project.getPluginManager().apply(ReportingBasePlugin.class);

        JavaPluginConvention javaConvention = addExtensions(project);

        configureSourceSetDefaults(javaConvention);
        configureCompileDefaults(project, javaConvention);

        configureJavaDoc(project, javaConvention);
        configureTest(project, javaConvention);
        configureBuildNeeded(project);
        configureBuildDependents(project);
        configureSchema(project);
        bridgeToSoftwareModelIfNecessary(project);
        configureVariantDerivationStrategy(project);
    }

    private void configureVariantDerivationStrategy(ProjectInternal project) {
        ComponentMetadataHandlerInternal metadataHandler = (ComponentMetadataHandlerInternal) project.getDependencies().getComponents();
        metadataHandler.setVariantDerivationStrategy(new JavaEcosystemVariantDerivationStrategy());
    }

    private JavaPluginConvention addExtensions(final ProjectInternal project) {
        JavaPluginConvention javaConvention = new DefaultJavaPluginConvention(project, objectFactory);
        project.getConvention().getPlugins().put("java", javaConvention);
        project.getExtensions().add(SourceSetContainer.class, "sourceSets", javaConvention.getSourceSets());
        project.getExtensions().create(JavaPluginExtension.class, "java", DefaultJavaPluginExtension.class, javaConvention, project);
        return javaConvention;
    }

    private void bridgeToSoftwareModelIfNecessary(ProjectInternal project) {
        project.addRuleBasedPluginListener(new MyRuleBasedPluginListener());
    }

    private void configureSchema(ProjectInternal project) {
        AttributesSchema attributesSchema = project.getDependencies().getAttributesSchema();
        JavaEcosystemSupport.configureSchema(attributesSchema, objectFactory);
        project.getDependencies().getArtifactTypes().create(ArtifactTypeDefinition.JAR_TYPE).getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_JARS));
    }



    private void configureSourceSetDefaults(final JavaPluginConvention pluginConvention) {
        final Project project = pluginConvention.getProject();
        pluginConvention.getSourceSets().all(new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet sourceSet) {
                ConventionMapping outputConventionMapping = ((IConventionAware) sourceSet.getOutput()).getConventionMapping();

                ConfigurationContainer configurations = project.getConfigurations();

                defineConfigurationsForSourceSet(sourceSet, configurations, pluginConvention);
                definePathsForSourceSet(sourceSet, outputConventionMapping, project);

                createProcessResourcesTask(sourceSet, sourceSet.getResources(), project);
                Provider<JavaCompile> compileTask = createCompileJavaTask(sourceSet, sourceSet.getJava(), project);
                createClassesTask(sourceSet, project);

                SourceSetUtil.configureOutputDirectoryForSourceSet(sourceSet, sourceSet.getJava(), project, compileTask, compileTask.map(new CompileOptionsJavaCompileTransformer()));
            }
        });
    }

    Provider<JavaCompile> createCompileJavaTask(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target) {
        return target.getTasks().register(sourceSet.getCompileJavaTaskName(), JavaCompile.class, new JavaCompileAction(sourceDirectorySet, sourceSet, target));
    }

    void createProcessResourcesTask(final SourceSet sourceSet, final SourceDirectorySet resourceSet, final Project target) {
        target.getTasks().register(sourceSet.getProcessResourcesTaskName(), ProcessResources.class, new ProcessResourcesAction(resourceSet, sourceSet));
    }

    void createClassesTask(final SourceSet sourceSet, Project target) {
        Provider<Task> classesTask = target.getTasks().register(sourceSet.getClassesTaskName(), new TaskAction222(sourceSet));
        sourceSet.compiledBy(classesTask);
    }

    void definePathsForSourceSet(final SourceSet sourceSet, ConventionMapping outputConventionMapping, final Project project) {
        outputConventionMapping.map("resourcesDir", new MyCallable2222222(sourceSet, project));

        sourceSet.getJava().srcDir("src/" + sourceSet.getName() + "/java");
        sourceSet.getResources().srcDir("src/" + sourceSet.getName() + "/resources");
    }

    void defineConfigurationsForSourceSet(SourceSet sourceSet, ConfigurationContainer configurations, final JavaPluginConvention convention) {
        String compileConfigurationName = sourceSet.getCompileConfigurationName();
        String implementationConfigurationName = sourceSet.getImplementationConfigurationName();
        String runtimeConfigurationName = sourceSet.getRuntimeConfigurationName();
        String runtimeOnlyConfigurationName = sourceSet.getRuntimeOnlyConfigurationName();
        String compileOnlyConfigurationName = sourceSet.getCompileOnlyConfigurationName();
        String compileClasspathConfigurationName = sourceSet.getCompileClasspathConfigurationName();
        String annotationProcessorConfigurationName = sourceSet.getAnnotationProcessorConfigurationName();
        String runtimeClasspathConfigurationName = sourceSet.getRuntimeClasspathConfigurationName();
        String sourceSetName = sourceSet.toString();
        Action<ConfigurationInternal> configureDefaultTargetPlatform = configureDefaultTargetPlatform(convention);


        Configuration compileConfiguration = configurations.maybeCreate(compileConfigurationName);
        compileConfiguration.setVisible(false);
        compileConfiguration.setDescription("Dependencies for " + sourceSetName + " (deprecated, use '" + implementationConfigurationName + "' instead).");

        Configuration implementationConfiguration = configurations.maybeCreate(implementationConfigurationName);
        implementationConfiguration.setVisible(false);
        implementationConfiguration.setDescription("Implementation only dependencies for " + sourceSetName + ".");
        implementationConfiguration.setCanBeConsumed(false);
        implementationConfiguration.setCanBeResolved(false);
        implementationConfiguration.extendsFrom(compileConfiguration);

        Configuration runtimeConfiguration = configurations.maybeCreate(runtimeConfigurationName);
        runtimeConfiguration.setVisible(false);
        runtimeConfiguration.extendsFrom(compileConfiguration);
        runtimeConfiguration.setDescription("Runtime dependencies for " + sourceSetName + " (deprecated, use '" + runtimeOnlyConfigurationName + "' instead).");

        Configuration compileOnlyConfiguration = configurations.maybeCreate(compileOnlyConfigurationName);
        compileOnlyConfiguration.setVisible(false);
        compileOnlyConfiguration.setDescription("Compile only dependencies for " + sourceSetName + ".");

        Configuration compileClasspathConfiguration = configurations.maybeCreate(compileClasspathConfigurationName);
        compileClasspathConfiguration.setVisible(false);
        compileClasspathConfiguration.extendsFrom(compileOnlyConfiguration, implementationConfiguration);
        compileClasspathConfiguration.setDescription("Compile classpath for " + sourceSetName + ".");
        compileClasspathConfiguration.setCanBeConsumed(false);
        compileClasspathConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API));
        compileClasspathConfiguration.getAttributes().attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        ((ConfigurationInternal)compileClasspathConfiguration).beforeLocking(configureDefaultTargetPlatform);

        Configuration annotationProcessorConfiguration = configurations.maybeCreate(annotationProcessorConfigurationName);
        annotationProcessorConfiguration.setVisible(false);
        annotationProcessorConfiguration.setDescription("Annotation processors and their dependencies for " + sourceSetName + ".");
        annotationProcessorConfiguration.setCanBeConsumed(false);
        annotationProcessorConfiguration.setCanBeResolved(true);

        Configuration runtimeOnlyConfiguration = configurations.maybeCreate(runtimeOnlyConfigurationName);
        runtimeOnlyConfiguration.setVisible(false);
        runtimeOnlyConfiguration.setCanBeConsumed(false);
        runtimeOnlyConfiguration.setCanBeResolved(false);
        runtimeOnlyConfiguration.setDescription("Runtime only dependencies for " + sourceSetName + ".");

        Configuration runtimeClasspathConfiguration = configurations.maybeCreate(runtimeClasspathConfigurationName);
        runtimeClasspathConfiguration.setVisible(false);
        runtimeClasspathConfiguration.setCanBeConsumed(false);
        runtimeClasspathConfiguration.setCanBeResolved(true);
        runtimeClasspathConfiguration.setDescription("Runtime classpath of " + sourceSetName + ".");
        runtimeClasspathConfiguration.extendsFrom(runtimeOnlyConfiguration, runtimeConfiguration, implementationConfiguration);
        runtimeClasspathConfiguration.getAttributes().attribute(USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME));
        runtimeClasspathConfiguration.getAttributes().attribute(BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
        ((ConfigurationInternal)runtimeClasspathConfiguration).beforeLocking(configureDefaultTargetPlatform);

        sourceSet.setCompileClasspath(compileClasspathConfiguration);
        sourceSet.setRuntimeClasspath(sourceSet.getOutput().plus(runtimeClasspathConfiguration));
        sourceSet.setAnnotationProcessorPath(annotationProcessorConfiguration);
    }

    private Action<ConfigurationInternal> configureDefaultTargetPlatform(final JavaPluginConvention convention) {
        return new ConfigurationInternalAction(convention);
    }

    private void configureCompileDefaults(final Project project, final JavaPluginConvention javaConvention) {
        project.getTasks().withType(AbstractCompile.class).configureEach(new AbstractCompileAction(javaConvention));
    }

    private void configureJavaDoc(final Project project, final JavaPluginConvention convention) {
        project.getTasks().withType(Javadoc.class).configureEach(new JavadocAction(convention, project));
    }

    private void configureBuildNeeded(Project project) {
        project.getTasks().register(BUILD_NEEDED_TASK_NAME, new TaskAction22());
    }

    private void configureBuildDependents(Project project) {
        project.getTasks().register(BUILD_DEPENDENTS_TASK_NAME, new TaskAction2());
    }

    private void configureTest(final Project project, final JavaPluginConvention convention) {
        project.getTasks().withType(Test.class).configureEach(new Action<Test>() {
            @Override
            public void execute(final Test test) {
                configureTestDefaults(test, project, convention);
            }
        });
    }

    void configureTestDefaults(final Test test, Project project, final JavaPluginConvention convention) {
        DslObject htmlReport = new DslObject(test.getReports().getHtml());
        DslObject xmlReport = new DslObject(test.getReports().getJunitXml());

        xmlReport.getConventionMapping().map("destination", new MyCallable222(convention, test));
        htmlReport.getConventionMapping().map("destination", new MyCallable2(convention, test));
        test.getConventionMapping().map("binResultsDir", new MyCallable(convention, test));
        test.workingDir(project.getProjectDir());
    }

    private static class MyCallable implements Callable<Object> {
        private final JavaPluginConvention convention;
        private final Test test;

        public MyCallable(JavaPluginConvention convention, Test test) {
            this.convention = convention;
            this.test = test;
        }

        @Override
        public Object call() {
            return new File(convention.getTestResultsDir(), test.getName() + "/binary");
        }
    }

    private static class MyCallable2 implements Callable<Object> {
        private final JavaPluginConvention convention;
        private final Test test;

        public MyCallable2(JavaPluginConvention convention, Test test) {
            this.convention = convention;
            this.test = test;
        }

        @Override
        public Object call() {
            return new File(convention.getTestReportDir(), test.getName());
        }
    }

    private static class MyCallable222 implements Callable<Object> {
        private final JavaPluginConvention convention;
        private final Test test;

        public MyCallable222(JavaPluginConvention convention, Test test) {
            this.convention = convention;
            this.test = test;
        }

        @Override
        public Object call() {
            return new File(convention.getTestResultsDir(), test.getName());
        }
    }

    private static class TaskAction implements Action<Task> {
        @Override
        public void execute(Task task) {
            if (!task.getProject().getGradle().getIncludedBuilds().isEmpty()) {
                task.getProject().getLogger().warn("[composite-build] Warning: `" + task.getPath() + "` task does not build included builds.");
            }
        }
    }

    private static class TaskAction2 implements Action<Task> {
        @Override
        public void execute(Task buildTask) {
            buildTask.setDescription("Assembles and tests this project and all projects that depend on it.");
            buildTask.setGroup(BasePlugin.BUILD_GROUP);
            buildTask.dependsOn(BUILD_TASK_NAME);
            buildTask.doFirst(new TaskAction());
        }
    }

    private static class TaskAction22 implements Action<Task> {
        @Override
        public void execute(Task buildTask) {
            buildTask.setDescription("Assembles and tests this project and all projects it depends on.");
            buildTask.setGroup(BasePlugin.BUILD_GROUP);
            buildTask.dependsOn(BUILD_TASK_NAME);
        }
    }

    private static class MyCallable22 implements Callable<Object> {
        private final Project project;

        public MyCallable22(Project project) {
            this.project = project;
        }

        @Override
        public Object call() {
            return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
        }
    }

    private static class MyCallable2222 implements Callable<Object> {
        private final JavaPluginConvention convention;

        public MyCallable2222(JavaPluginConvention convention) {
            this.convention = convention;
        }

        @Override
        public Object call() {
            return new File(convention.getDocsDir(), "javadoc");
        }
    }

    private static class JavadocAction implements Action<Javadoc> {
        private final JavaPluginConvention convention;
        private final Project project;

        public JavadocAction(JavaPluginConvention convention, Project project) {
            this.convention = convention;
            this.project = project;
        }

        @Override
        public void execute(Javadoc javadoc) {
            javadoc.getConventionMapping().map("destinationDir", new MyCallable2222(convention));
            javadoc.getConventionMapping().map("title", new MyCallable22(project));
        }
    }

    private static class MyCallable22222 implements Callable<Object> {
        private final JavaPluginConvention javaConvention;

        public MyCallable22222(JavaPluginConvention javaConvention) {
            this.javaConvention = javaConvention;
        }

        @Override
        public Object call() {
            return javaConvention.getTargetCompatibility().toString();
        }
    }

    private static class MyCallable222222 implements Callable<Object> {
        private final JavaPluginConvention javaConvention;

        public MyCallable222222(JavaPluginConvention javaConvention) {
            this.javaConvention = javaConvention;
        }

        @Override
        public Object call() {
            return javaConvention.getSourceCompatibility().toString();
        }
    }

    private static class AbstractCompileAction implements Action<AbstractCompile> {
        private final JavaPluginConvention javaConvention;

        public AbstractCompileAction(JavaPluginConvention javaConvention) {
            this.javaConvention = javaConvention;
        }

        @Override
        public void execute(final AbstractCompile compile) {
            ConventionMapping conventionMapping = compile.getConventionMapping();
            conventionMapping.map("sourceCompatibility", new MyCallable222222(javaConvention));
            conventionMapping.map("targetCompatibility", new MyCallable22222(javaConvention));
        }
    }

    private static class ConfigurationInternalAction implements Action<ConfigurationInternal> {
        private final JavaPluginConvention convention;

        public ConfigurationInternalAction(JavaPluginConvention convention) {
            this.convention = convention;
        }

        @Override
        public void execute(ConfigurationInternal conf) {
            if (!convention.getAutoTargetJvmDisabled()) {
                JavaEcosystemSupport.configureDefaultTargetPlatform(conf, convention.getTargetCompatibility());
            }
        }
    }

    private static class MyCallable2222222 implements Callable<Object> {
        private final SourceSet sourceSet;
        private final Project project;

        public MyCallable2222222(SourceSet sourceSet, Project project) {
            this.sourceSet = sourceSet;
            this.project = project;
        }

        @Override
        public Object call() {
            String classesDirName = "resources/" + sourceSet.getName();
            return new File(project.getBuildDir(), classesDirName);
        }
    }

    private static class TaskAction222 implements Action<Task> {
        private final SourceSet sourceSet;

        public TaskAction222(SourceSet sourceSet) {
            this.sourceSet = sourceSet;
        }

        @Override
        public void execute(Task classesTask) {
            classesTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            classesTask.setDescription("Assembles " + sourceSet.getOutput() + ".");
            classesTask.dependsOn(sourceSet.getOutput().getDirs());
            classesTask.dependsOn(sourceSet.getCompileJavaTaskName());
            classesTask.dependsOn(sourceSet.getProcessResourcesTaskName());
        }
    }

    private static class FileCallable implements Callable<File> {
        private final SourceSet sourceSet;

        public FileCallable(SourceSet sourceSet) {
            this.sourceSet = sourceSet;
        }

        @Override
        public File call() {
            return sourceSet.getOutput().getResourcesDir();
        }
    }

    private static class ProcessResourcesAction implements Action<ProcessResources> {
        private final SourceDirectorySet resourceSet;
        private final SourceSet sourceSet;

        public ProcessResourcesAction(SourceDirectorySet resourceSet, SourceSet sourceSet) {
            this.resourceSet = resourceSet;
            this.sourceSet = sourceSet;
        }

        @Override
        public void execute(ProcessResources resourcesTask) {
            resourcesTask.setDescription("Processes " + resourceSet + ".");
            new DslObject(resourcesTask.getRootSpec()).getConventionMapping().map("destinationDir", new FileCallable(sourceSet));
            resourcesTask.from(resourceSet);
        }
    }

    private static class FileCallable2 implements Callable<File> {
        private final SourceDirectorySet sourceDirectorySet;

        public FileCallable2(SourceDirectorySet sourceDirectorySet) {
            this.sourceDirectorySet = sourceDirectorySet;
        }

        @Override
        public File call() {
            return sourceDirectorySet.getOutputDir();
        }
    }

    private static class MyCallable22222222 implements Callable<Object> {
        private final SourceSet sourceSet;

        public MyCallable22222222(SourceSet sourceSet) {
            this.sourceSet = sourceSet;
        }

        @Override
        public Object call() {
            return sourceSet.getCompileClasspath();
        }
    }

    private static class JavaCompileAction implements Action<JavaCompile> {
        private final SourceDirectorySet sourceDirectorySet;
        private final SourceSet sourceSet;
        private final Project target;

        public JavaCompileAction(SourceDirectorySet sourceDirectorySet, SourceSet sourceSet, Project target) {
            this.sourceDirectorySet = sourceDirectorySet;
            this.sourceSet = sourceSet;
            this.target = target;
        }

        @Override
        public void execute(JavaCompile compileTask) {
            compileTask.setDescription("Compiles " + sourceDirectorySet + ".");
            compileTask.setSource(sourceDirectorySet);
            ConventionMapping conventionMapping = compileTask.getConventionMapping();
            conventionMapping.map("classpath", new MyCallable22222222(sourceSet));
            SourceSetUtil.configureAnnotationProcessorPath(sourceSet, sourceDirectorySet, compileTask.getOptions(), target);
            compileTask.setDestinationDir(target.provider(new FileCallable2(sourceDirectorySet)));
        }
    }

    private static class CompileOptionsJavaCompileTransformer implements Transformer<CompileOptions, JavaCompile> {
        @Override
        public CompileOptions transform(JavaCompile javaCompile) {
            return javaCompile.getOptions();
        }
    }

    private static class MyRuleBasedPluginListener implements RuleBasedPluginListener {
        @Override
        public void prepareForRuleBasedPlugins(Project project) {
            project.getPluginManager().apply(JavaBasePluginRules.class);
        }
    }
}
