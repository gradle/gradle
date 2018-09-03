/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.provider.AbstractMappingProvider;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.provider.Provider;

import java.io.File;

public class DefaultProjectLayout extends DefaultFilePropertyFactory implements ProjectLayout, TaskFileVarFactory {
    private final FixedDirectory projectDir;
    private final DefaultDirectoryVar buildDir;
    private final TaskResolver taskResolver;
    private final FileResolver fileResolver;

    public DefaultProjectLayout(File projectDir, FileResolver resolver, TaskResolver taskResolver) {
        super(resolver);
        this.taskResolver = taskResolver;
        this.fileResolver = resolver;
        this.projectDir = new FixedDirectory(projectDir, resolver);
        this.buildDir = new DefaultDirectoryVar(resolver, Project.DEFAULT_BUILD_DIR_NAME);
    }

    @Override
    public Directory getProjectDirectory() {
        return projectDir;
    }

    @Override
    public DirectoryProperty getBuildDirectory() {
        return buildDir;
    }

    @Override
    public DirectoryProperty directoryProperty(Provider<? extends Directory> initialProvider) {
        DirectoryProperty result = directoryProperty();
        result.set(initialProvider);
        return result;
    }

    @Override
    public RegularFileProperty fileProperty(Provider<? extends RegularFile> initialProvider) {
        RegularFileProperty result = fileProperty();
        result.set(initialProvider);
        return result;
    }

    @Override
    public DirectoryProperty newOutputDirectory(Task producer) {
        DefaultDirectoryVar property = new DefaultDirectoryVar(projectDir.fileResolver);
        property.attachProducer(producer);
        return property;
    }

    @Override
    public RegularFileProperty newOutputFile(Task producer) {
        DefaultRegularFileVar property = new DefaultRegularFileVar(projectDir.fileResolver);
        property.attachProducer(producer);
        return property;
    }

    @Override
    public RegularFileProperty newInputFile(final Task consumer) {
        final DefaultRegularFileVar fileVar = new DefaultRegularFileVar(projectDir.fileResolver);
        consumer.dependsOn(new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                fileVar.visitDependencies(context);
            }
        });
        return fileVar;
    }

    @Override
    public DirectoryProperty newInputDirectory(final Task consumer) {
        final DefaultDirectoryVar directoryVar = new DefaultDirectoryVar(projectDir.fileResolver);
        consumer.dependsOn(new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                directoryVar.visitDependencies(context);
            }
        });
        return directoryVar;
    }

    @Override
    public ConfigurableFileCollection newInputFileCollection(Task consumer) {
        return new CachingTaskInputFileCollection(consumer.getPath(), projectDir.fileResolver, taskResolver);
    }

    @Override
    public FileCollection newCalculatedInputFileCollection(Task consumer, MinimalFileSet calculatedFiles, FileCollection... inputs) {
        return new CalculatedTaskInputFileCollection(consumer.getPath(), calculatedFiles, inputs);
    }

    @Override
    public Provider<RegularFile> file(Provider<File> provider) {
        return new AbstractMappingProvider<RegularFile, File>(RegularFile.class, provider) {
            @Override
            protected RegularFile map(File file) {
                return new FixedFile(projectDir.fileResolver.resolve(file));
            }
        };
    }

    @Override
    public FileCollection files(Object... paths) {
        return ImmutableFileCollection.usingResolver(fileResolver, paths);
    }

    @Override
    public ConfigurableFileCollection configurableFiles(Object... files) {
        return new DefaultConfigurableFileCollection(fileResolver, taskResolver, files);
    }

    /**
     * A temporary home. Should be on the public API somewhere
     */
    public void setBuildDirectory(Object value) {
        buildDir.resolveAndSet(value);
    }
}
