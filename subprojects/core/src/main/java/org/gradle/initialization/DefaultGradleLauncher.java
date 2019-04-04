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
package org.gradle.initialization;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.DefaultCompositeFileTree;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.service.scopes.BuildScopeServiceRegistryFactory;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;

import javax.annotation.Nullable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultGradleLauncher implements GradleLauncher {

    private static final ConfigureBuildBuildOperationType.Result CONFIGURE_BUILD_RESULT = new ConfigureBuildBuildOperationType.Result() {
    };
    private static final NotifyProjectsEvaluatedBuildOperationType.Result PROJECTS_EVALUATED_RESULT = new NotifyProjectsEvaluatedBuildOperationType.Result() {
    };

    private enum Stage {
        LoadSettings, Configure, TaskGraph, RunTasks() {
            @Override
            String getDisplayName() {
                return "Build";
            }
        }, Finished;

        String getDisplayName() {
            return name();
        }
    }

    private static final LoadBuildBuildOperationType.Result RESULT = new LoadBuildBuildOperationType.Result() {
    };

    private final InitScriptHandler initScriptHandler;
    private final SettingsLoader settingsLoader;
    private final BuildLoader buildLoader;
    private final BuildConfigurer buildConfigurer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final ModelConfigurationListener modelConfigurationListener;
    private final BuildCompletionListener buildCompletionListener;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildConfigurationActionExecuter buildConfigurationActionExecuter;
    private final BuildExecuter buildExecuter;
    private final BuildScopeServices buildServices;
    private final List<?> servicesToStop;
    private final IncludedBuildControllers includedBuildControllers;
    private final PublicBuildPath fromBuild;
    private final GradleInternal gradle;
    private SettingsInternal settings;
    private Stage stage;

    public DefaultGradleLauncher(GradleInternal gradle, InitScriptHandler initScriptHandler, SettingsLoader settingsLoader, BuildLoader buildLoader,
                                 BuildConfigurer buildConfigurer, ExceptionAnalyser exceptionAnalyser,
                                 BuildListener buildListener, ModelConfigurationListener modelConfigurationListener,
                                 BuildCompletionListener buildCompletionListener, BuildOperationExecutor operationExecutor,
                                 BuildConfigurationActionExecuter buildConfigurationActionExecuter, BuildExecuter buildExecuter,
                                 BuildScopeServices buildServices, List<?> servicesToStop, IncludedBuildControllers includedBuildControllers, PublicBuildPath fromBuild) {
        this.gradle = gradle;
        this.initScriptHandler = initScriptHandler;
        this.settingsLoader = settingsLoader;
        this.buildLoader = buildLoader;
        this.buildConfigurer = buildConfigurer;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.modelConfigurationListener = modelConfigurationListener;
        this.buildOperationExecutor = operationExecutor;
        this.buildConfigurationActionExecuter = buildConfigurationActionExecuter;
        this.buildExecuter = buildExecuter;
        this.buildCompletionListener = buildCompletionListener;
        this.buildServices = buildServices;
        this.servicesToStop = servicesToStop;
        this.includedBuildControllers = includedBuildControllers;
        this.fromBuild = fromBuild;
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        doBuildStages(Stage.LoadSettings);
        return settings;
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        doBuildStages(Stage.Configure);
        return gradle;
    }

    public GradleInternal executeTasks() {
        doBuildStages(Stage.RunTasks);
        return gradle;
    }

    @Override
    public void finishBuild() {
        if (stage != null) {
            finishBuild(stage.getDisplayName(), null);
        }
    }

    private void doBuildStages(Stage upTo) {
        try {
            if (upTo == Stage.RunTasks && isInstantExecutionEnabled() && instantExecutionStateFile().isFile()) {
                doInstantExecution();
            } else {
                doClassicBuildStages(upTo);
            }
            finishBuild();
        } catch (Throwable t) {
            finishBuild(upTo.getDisplayName(), t);
        }
    }

    private boolean isInstantExecutionEnabled() {
        return gradle.getStartParameter().getSystemPropertiesArgs().get("instantExecution") != null;
    }

    private void doInstantExecution() {
        ClassLoaderScopeRegistry classLoaderScopeRegistry = gradle.getServices().get(ClassLoaderScopeRegistry.class);
        ScriptHandlerFactory scriptHandlerFactory = gradle.getServices().get(ScriptHandlerFactory.class);
        StringScriptSource settingsSource = new StringScriptSource("settings", "");
        final ProjectDescriptorRegistry projectDescriptorRegistry = new DefaultProjectDescriptorRegistry();
        DefaultSettings settings = new DefaultSettings(
            gradle.getServices().get(BuildScopeServiceRegistryFactory.class),
            gradle,
            classLoaderScopeRegistry.getCoreScope(),
            classLoaderScopeRegistry.getCoreScope(),
            scriptHandlerFactory.create(settingsSource, classLoaderScopeRegistry.getCoreScope()),
            new File(".").getAbsoluteFile(),
            settingsSource,
            gradle.getStartParameter()
        ) {
            @Override
            public ExtensionContainer getExtensions() {
                return null;
            }

            @Override
            public ProjectDescriptorRegistry getProjectDescriptorRegistry() {
                return projectDescriptorRegistry;
            }

            @Override
            protected FileResolver getFileResolver() {
                return gradle.getServices().get(FileResolver.class);
            }
        };
        gradle.setSettings(settings);

        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(
            null, ":", new File(".").getAbsoluteFile(),
            projectDescriptorRegistry, gradle.getServices().get(PathToFileResolver.class)
        );

        IProjectFactory projectFactory = gradle.getServices().get(IProjectFactory.class);

        ProjectInternal rootProject = projectFactory.createProject(
            projectDescriptor, null, gradle,
            classLoaderScopeRegistry.getCoreAndPluginsScope(),
            classLoaderScopeRegistry.getCoreAndPluginsScope()
        );
        gradle.setRootProject(rootProject);

        gradle.getServices().get(ProjectStateRegistry.class)
            .registerProjects(gradle.getServices().get(BuildState.class));

        TaskExecutionGraphInternal taskGraph = gradle.getTaskGraph();
        loadInstantExecutionStateInto(taskGraph, rootProject, classLoaderScopeRegistry);
        taskGraph.populate();
        stage = Stage.TaskGraph;
        runTasks();
    }

    private void loadInstantExecutionStateInto(TaskExecutionGraphInternal taskGraph, Project rootProject, ClassLoaderScopeRegistry classLoaderScopeRegistry) {
        final Serializer<Object> propertyValueSerializer = propertyValueSerializer();

        try {
            KryoBackedDecoder decoder = new KryoBackedDecoder(new FileInputStream(instantExecutionStateFile()));
            try {
                int count = decoder.readSmallInt();
                for (int i = 0; i < count; i++) {
                    String taskName = decoder.readString();
                    String typeName = decoder.readString();
                    Class<? extends Task> taskClass = loadTaskClass(classLoaderScopeRegistry, typeName);
                    ClassDetails details = ClassInspector.inspect(taskClass);
                    Task task = rootProject.getTasks().create(taskName, taskClass);
                    String propertyName;
                    while (!(propertyName = decoder.readString()).isEmpty()) {
                        Object value = propertyValueSerializer.read(decoder);
                        if (value == null) {
                            continue;
                        }
                        PropertyDetails property = details.getProperty(propertyName);
                        for (Method setter : property.getSetters()) {
                            if (setter.getParameterTypes()[0].isAssignableFrom(value.getClass())) {
                                setter.setAccessible(true);
                                setter.invoke(task, value);
                                break;
                            }
                        }
                    }
                    taskGraph.addEntryTasks(Collections.singleton(
                        task
                    ));
                }
            } finally {
                decoder.close();
            }
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    private DefaultGradleLauncher.PropertyValueSerializer propertyValueSerializer() {
        return new PropertyValueSerializer(gradle.getServices().get(DirectoryFileTreeFactory.class), gradle.getServices().get(FileCollectionFactory.class));
    }

    private Class<? extends Task> loadTaskClass(ClassLoaderScopeRegistry classLoaderScopeRegistry, String typeName) {
        try {
            return (Class<? extends Task>) classLoaderScopeRegistry.getCoreAndPluginsScope().getLocalClassLoader()
                .loadClass(typeName);
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private File instantExecutionStateFile() {
        return new File(".instant-execution-state");
    }

    private void doClassicBuildStages(Stage upTo) {
        loadSettings();
        if (upTo == Stage.LoadSettings) {
            return;
        }
        configureBuild();
        if (upTo == Stage.Configure) {
            return;
        }
        constructTaskGraph();
        if (upTo == Stage.TaskGraph) {
            return;
        }
        if (isInstantExecutionEnabled()) {
            saveInstantExecutionState();
        }
        runTasks();
    }

    private void saveInstantExecutionState() {
        final PropertyValueSerializer propertyValueSerializer = propertyValueSerializer();

        try {
            final KryoBackedEncoder encoder = new KryoBackedEncoder(new FileOutputStream(instantExecutionStateFile()));
            try {
                List<Task> allTasks = gradle.getTaskGraph().getAllTasks();
                encoder.writeSmallInt(allTasks.size());
                for (Task task : allTasks) {
                    if (task.getProject().getParent() != null) {
                        throw new UnsupportedOperationException("Tasks must be in the root project");
                    }
                    Class<?> taskType = GeneratedSubclasses.unpack(task.getClass());
                    encoder.writeString(task.getName());
                    encoder.writeString(taskType.getName());
                    for (PropertyDetails property : ClassInspector.inspect(taskType).getProperties()) {
                        if (property.getSetters().isEmpty() || property.getGetters().isEmpty()) {
                            continue;
                        }
                        Method getter = property.getGetters().get(0);
                        if (!(propertyValueSerializer.canWrite(getter.getReturnType()))) {
                            continue;
                        }
                        getter.setAccessible(true);
                        Object finalValue = getter.invoke(task);
                        encoder.writeString(property.getName());
                        try {
                            propertyValueSerializer.write(encoder, finalValue);
                        } catch (Exception e) {
                            throw UncheckedException.throwAsUncheckedException(e);
                        }
                    }
                    encoder.writeString("");
                }
            } finally {
                encoder.close();
            }
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    class PropertyValueSerializer implements Serializer<Object> {
        private final Serializer<Set<File>> fileSetSerializer = new SetSerializer<File>(BaseSerializerFactory.FILE_SERIALIZER);
        private final Serializer<String> stringSerializer = BaseSerializerFactory.STRING_SERIALIZER;
        private final DirectoryFileTreeFactory directoryFileTreeFactory;
        private final FileCollectionFactory fileCollectionFactory;
        private final Serializer<FileTreeInternal> fileTreeSerializer = new Serializer<FileTreeInternal>() {
            @Override
            public FileTreeInternal read(Decoder decoder) throws Exception {
                Set<File> roots = fileSetSerializer.read(decoder);
                List<FileTreeInternal> fileTrees = new ArrayList<FileTreeInternal>(roots.size());
                for (File root : roots) {
                    fileTrees.add(new FileTreeAdapter(directoryFileTreeFactory.create(root)));
                }
                return new DefaultCompositeFileTree(fileTrees);
            }

            @Override
            public void write(Encoder encoder, FileTreeInternal value) throws Exception {
                FileTreeVisitor visitor = new FileTreeVisitor();
                value.visitLeafCollections(visitor);
                fileSetSerializer.write(encoder, visitor.roots);
            }
        };
        private static final byte NULL_VALUE = 0;
        private static final byte STRING_TYPE = 1;
        private static final byte FILE_TREE_TYPE = 2;
        private static final byte FILE_TYPE = 3;
        private static final byte FILE_COLLECTION_TYPE = 4;

        public PropertyValueSerializer(DirectoryFileTreeFactory directoryFileTreeFactory, FileCollectionFactory fileCollectionFactory) {
            this.directoryFileTreeFactory = directoryFileTreeFactory;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public Object read(Decoder decoder) throws EOFException, Exception {
            byte tag = decoder.readByte();
            switch (tag) {
                case NULL_VALUE:
                    return null;
                case STRING_TYPE:
                    return stringSerializer.read(decoder);
                case FILE_TREE_TYPE:
                    return fileTreeSerializer.read(decoder);
                case FILE_TYPE:
                    return BaseSerializerFactory.FILE_SERIALIZER.read(decoder);
                case FILE_COLLECTION_TYPE:
                    return fileCollectionFactory.fixed(fileSetSerializer.read(decoder));
                default:
                    throw new UnsupportedOperationException();
            }
        }

        @Override
        public void write(Encoder encoder, Object value) throws Exception {
            if (value == null) {
                encoder.writeByte(NULL_VALUE);
            } else if (value instanceof String) {
                encoder.writeByte(STRING_TYPE);
                stringSerializer.write(encoder, (String) value);
            } else if (value instanceof FileTreeInternal) {
                encoder.writeByte(FILE_TREE_TYPE);
                fileTreeSerializer.write(encoder, (FileTreeInternal) value);
            } else if (value instanceof File) {
                encoder.writeByte(FILE_TYPE);
                BaseSerializerFactory.FILE_SERIALIZER.write(encoder, (File) value);
            } else if (value instanceof FileCollection) {
                encoder.writeByte(FILE_COLLECTION_TYPE);
                fileSetSerializer.write(encoder, ((FileCollection)value).getFiles());
            } else {
                throw new UnsupportedOperationException();
            }
        }

        public boolean canWrite(Class<?> type) {
            return type.equals(String.class) || type.equals(FileTree.class) || type.equals(File.class) || type.equals(FileCollection.class);
        }
    }

    private void finishBuild(String action, @Nullable Throwable stageFailure) {
        if (stage == Stage.Finished) {
            return;
        }

        RuntimeException reportableFailure = stageFailure == null ? null : exceptionAnalyser.transform(stageFailure);
        BuildResult buildResult = new BuildResult(action, gradle, reportableFailure);
        List<Throwable> failures = new ArrayList<Throwable>();
        includedBuildControllers.finishBuild(failures);
        try {
            buildListener.buildFinished(buildResult);
        } catch (Throwable t) {
            failures.add(t);
        }
        stage = Stage.Finished;

        if (failures.isEmpty() && reportableFailure != null) {
            throw reportableFailure;
        }
        if (!failures.isEmpty()) {
            if (stageFailure instanceof MultipleBuildFailures) {
                failures.addAll(0, ((MultipleBuildFailures) stageFailure).getCauses());
            } else if (stageFailure != null) {
                failures.add(0, stageFailure);
            }
            throw exceptionAnalyser.transform(new MultipleBuildFailures(failures));
        }
    }

    private void loadSettings() {
        if (stage == null) {
            buildListener.buildStarted(gradle);

            buildOperationExecutor.run(new LoadBuild());

            stage = Stage.LoadSettings;
        }
    }

    private void configureBuild() {
        if (stage == Stage.LoadSettings) {
            buildOperationExecutor.run(new ConfigureBuild());

            stage = Stage.Configure;
        }
    }

    private void constructTaskGraph() {
        if (stage == Stage.Configure) {
            buildOperationExecutor.run(new CalculateTaskGraph());

            stage = Stage.TaskGraph;
        }
    }

    @Override
    public void scheduleTasks(final Iterable<String> taskPaths) {
        GradleInternal gradle = getConfiguredBuild();
        Set<String> allTasks = Sets.newLinkedHashSet(gradle.getStartParameter().getTaskNames());
        boolean added = allTasks.addAll(Lists.newArrayList(taskPaths));

        if (!added) {
            return;
        }

        gradle.getStartParameter().setTaskNames(allTasks);

        // Force back to configure so that task graph will get reevaluated
        stage = Stage.Configure;

        doBuildStages(Stage.TaskGraph);
    }

    private void runTasks() {
        if (stage != Stage.TaskGraph) {
            throw new IllegalStateException("Cannot execute tasks: current stage = " + stage);
        }

        buildOperationExecutor.run(new ExecuteTasks());

        stage = Stage.RunTasks;
    }

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of the build. See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for
     * supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addListener(Object listener) {
        gradle.addListener(listener);
    }

    public void stop() {
        try {
            CompositeStoppable.stoppable(buildServices).add(servicesToStop).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }

    private static class FileTreeVisitor implements FileCollectionLeafVisitor {
        Set<File> roots = new LinkedHashSet<File>();

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitGenericFileTree(FileTreeInternal fileTree) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns) {
            roots.add(root);
        }
    }

    private class LoadBuild implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            // Evaluate init scripts
            initScriptHandler.executeScripts(gradle);
            // Build `buildSrc`, load settings.gradle, and construct composite (if appropriate)
            settings = settingsLoader.findAndLoadSettings(gradle);
            context.setResult(RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Load build"))
                .details(new LoadBuildBuildOperationType.Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().toString();
                    }

                    @Override
                    public String getIncludedBy() {
                        return fromBuild == null ? null : fromBuild.getBuildPath().toString();
                    }
                });
        }
    }

    private class ConfigureBuild implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            buildLoader.load(settings, gradle);
            buildConfigurer.configure(gradle);

            if (!isConfigureOnDemand()) {
                projectsEvaluated();
            }

            modelConfigurationListener.onConfigure(gradle);
            context.setResult(CONFIGURE_BUILD_RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Configure build"));
            if (gradle.getParent() == null) {
                builder.operationType(BuildOperationCategory.CONFIGURE_ROOT_BUILD);
            } else {
                builder.operationType(BuildOperationCategory.CONFIGURE_BUILD);
            }
            builder.totalProgress(settings.getProjectRegistry().size());
            return builder.details(new ConfigureBuildBuildOperationType.Details() {
                @Override
                public String getBuildPath() {
                    return getGradle().getIdentityPath().toString();
                }
            });
        }
    }

    private class CalculateTaskGraph implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext buildOperationContext) {
            buildConfigurationActionExecuter.select(gradle);

            if (isConfigureOnDemand()) {
                projectsEvaluated();
            }

            final TaskExecutionGraphInternal taskGraph = gradle.getTaskGraph();
            taskGraph.populate();

            includedBuildControllers.populateTaskGraphs();

            buildOperationContext.setResult(new CalculateTaskGraphBuildOperationType.Result() {
                @Override
                public List<String> getRequestedTaskPaths() {
                    return toTaskPaths(taskGraph.getRequestedTasks());
                }

                @Override
                public List<String> getExcludedTaskPaths() {
                    return toTaskPaths(taskGraph.getFilteredTasks());
                }

                private List<String> toTaskPaths(Set<Task> tasks) {
                    return ImmutableSortedSet.copyOf(Collections2.transform(tasks, new Function<Task, String>() {
                        @Override
                        public String apply(Task task) {
                            return task.getPath();
                        }
                    })).asList();
                }
            });
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Calculate task graph"))
                .details(new CalculateTaskGraphBuildOperationType.Details() {
                    @Override
                    public String getBuildPath() {
                        return getGradle().getIdentityPath().getPath();
                    }
                });
        }
    }

    private class ExecuteTasks implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            includedBuildControllers.startTaskExecution();
            List<Throwable> taskFailures = new ArrayList<Throwable>();
            buildExecuter.execute(gradle, taskFailures);
            includedBuildControllers.awaitTaskCompletion(taskFailures);
            if (!taskFailures.isEmpty()) {
                throw new MultipleBuildFailures(taskFailures);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Run tasks"));
            if (gradle.getParent() == null) {
                builder.operationType(BuildOperationCategory.RUN_WORK_ROOT_BUILD);
            } else {
                builder.operationType(BuildOperationCategory.RUN_WORK);
            }
            builder.totalProgress(gradle.getTaskGraph().size());
            return builder;
        }
    }

    private class NotifyProjectsEvaluatedListeners implements RunnableBuildOperation {

        @Override
        public void run(BuildOperationContext context) {
            buildListener.projectsEvaluated(gradle);
            context.setResult(PROJECTS_EVALUATED_RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Notify projectsEvaluated listeners"))
                .details(new NotifyProjectsEvaluatedBuildOperationType.Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().toString();
                    }
                });
        }
    }

    private boolean isConfigureOnDemand() {
        return gradle.getStartParameter().isConfigureOnDemand();
    }

    private void projectsEvaluated() {
        buildOperationExecutor.run(new NotifyProjectsEvaluatedListeners());
    }

}
