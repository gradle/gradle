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

package org.gradle.initialization;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.service.scopes.BuildScopeServiceRegistryFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

class InstantExecution {
    private final GradleInternal gradle;

    public InstantExecution(GradleInternal gradle) {
        this.gradle = gradle;
    }

    private PropertyValueSerializer propertyValueSerializer() {
        return new PropertyValueSerializer(gradle.getServices().get(DirectoryFileTreeFactory.class), gradle.getServices().get(FileCollectionFactory.class));
    }

    private File instantExecutionStateFile() {
        return new File(".instant-execution-state");
    }

    private Class<? extends Task> loadTaskClass(ClassLoaderScopeRegistry classLoaderScopeRegistry, String typeName) {
        try {
            return (Class<? extends Task>) classLoaderScopeRegistry.getCoreAndPluginsScope().getLocalClassLoader()
                .loadClass(typeName);
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private boolean isInstantExecutionEnabled() {
        return gradle.getStartParameter().getSystemPropertiesArgs().get("instantExecution") != null;
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

    public void saveInstantExecutionState() {
        if (!isInstantExecutionEnabled()) {
            return;
        }

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

    public boolean canExecuteInstantaneously() {
        return isInstantExecutionEnabled() && instantExecutionStateFile().isFile();
    }

    public void prepareTaskGraph() {
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
    }
}
