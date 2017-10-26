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
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.internal.provider.AbstractCombiningProvider;
import org.gradle.api.internal.provider.AbstractMappingProvider;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.DefaultPropertyState;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultProjectLayout implements ProjectLayout, TaskFileVarFactory {
    private final FixedDirectory projectDir;
    private final DefaultDirectoryVar buildDir;

    public DefaultProjectLayout(File projectDir, FileResolver resolver) {
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
    public DirectoryVar newDirectoryVar() {
        DeprecationLogger.nagUserOfReplacedMethod("ProjectLayout.newDirectoryVar()", "ProjectLayout.directoryProperty()");
        return directoryProperty();
    }

    @Override
    public DirectoryVar directoryProperty() {
        return new DefaultDirectoryVar(projectDir.fileResolver);
    }

    @Override
    public DirectoryVar directoryProperty(Provider<? extends Directory> initialProvider) {
        DirectoryVar result = directoryProperty();
        result.set(initialProvider);
        return result;
    }

    @Override
    public RegularFileVar newFileVar() {
        DeprecationLogger.nagUserOfReplacedMethod("ProjectLayout.newFileVar()", "ProjectLayout.fileProperty()");
        return fileProperty();
    }

    @Override
    public RegularFileVar fileProperty() {
        return new DefaultRegularFileVar(projectDir.fileResolver);
    }

    @Override
    public RegularFileVar fileProperty(Provider<? extends RegularFile> initialProvider) {
        RegularFileVar result = fileProperty();
        result.set(initialProvider);
        return result;
    }

    @Override
    public DirectoryProperty newOutputDirectory(Task producer) {
        return new BuildableDirectoryVar(projectDir.fileResolver, producer);
    }

    @Override
    public RegularFileProperty newOutputFile(Task producer) {
        return new BuildableRegularFileVar(projectDir.fileResolver, producer);
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
    public Provider<RegularFile> file(Provider<File> provider) {
        return new AbstractMappingProvider<RegularFile, File>(RegularFile.class, provider) {
            @Override
            protected RegularFile map(File file) {
                return new FixedFile(projectDir.fileResolver.resolve(file));
            }
        };
    }

    /**
     * A temporary home. Should be on the public API somewhere
     */
    public void setBuildDirectory(Object value) {
        buildDir.resolveAndSet(value);
    }

    private static class FixedDirectory implements Directory, FileSystemLocation {
        private final File value;
        private final FileResolver fileResolver;

        FixedDirectory(File value, FileResolver fileResolver) {
            this.value = value;
            this.fileResolver = fileResolver;
        }

        @Override
        public String toString() {
            return value.toString();
        }

        @Override
        public File getAsFile() {
            return value;
        }

        @Override
        public Directory dir(String path) {
            File newDir = fileResolver.resolve(path);
            return new FixedDirectory(newDir, fileResolver.newResolver(newDir));
        }

        @Override
        public FileTree getAsFileTree() {
            return fileResolver.resolveFilesAsTree(this);
        }

        @Override
        public Provider<Directory> dir(Provider<? extends CharSequence> path) {
            return new ResolvingDirectory(fileResolver, path, path);
        }

        @Override
        public RegularFile file(String path) {
            return new FixedFile(fileResolver.resolve(path));
        }

        @Override
        public Provider<RegularFile> file(Provider<? extends CharSequence> path) {
            return new ResolvingFile(fileResolver, path);
        }
    }

    private static class FixedFile implements RegularFile, FileSystemLocation {
        private final File file;

        FixedFile(File file) {
            this.file = file;
        }

        @Override
        public String toString() {
            return file.toString();
        }

        @Override
        public File getAsFile() {
            return file;
        }
    }

    private static class ResolvingFile extends AbstractMappingProvider<RegularFile, CharSequence> implements TaskDependencyContainer {
        private final PathToFileResolver resolver;

        ResolvingFile(PathToFileResolver resolver, Provider<? extends CharSequence> path) {
            super(RegularFile.class, path);
            this.resolver = resolver;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            // No dependencies
        }

        @Override
        protected RegularFile map(CharSequence path) {
            return new FixedFile(resolver.resolve(path));
        }
    }

    private static class DefaultRegularFileVar extends DefaultPropertyState<RegularFile> implements RegularFileVar, TaskDependencyContainer {
        private final PathToFileResolver fileResolver;

        DefaultRegularFileVar(PathToFileResolver fileResolver) {
            super(RegularFile.class);
            this.fileResolver = fileResolver;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            if (getProvider() instanceof TaskDependencyContainer) {
                context.add(getProvider());
            }
        }

        @Override
        public void setFromAnyValue(Object object) {
            if (object instanceof File) {
                set((File) object);
            } else {
                super.setFromAnyValue(object);
            }
        }

        @Override
        public Provider<File> getAsFile() {
            return new ToFileProvider(this);
        }

        @Override
        public void set(File file) {
            set(new FixedFile(fileResolver.resolve(file)));
        }
    }

    private static class BuildableRegularFileVar extends DefaultRegularFileVar {
        private final Task producer;

        BuildableRegularFileVar(PathToFileResolver fileResolver, Task producer) {
            super(fileResolver);
            this.producer = producer;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(producer);
        }
    }

    private static class ResolvingDirectory extends AbstractProvider<Directory> implements TaskDependencyContainer {
        private final FileResolver resolver;
        private final Provider<?> valueProvider;
        private final Factory<File> valueFactory;

        ResolvingDirectory(FileResolver resolver, Object value, Provider<?> valueProvider) {
            this.resolver = resolver;
            this.valueProvider = valueProvider;
            this.valueFactory = resolver.resolveLater(value);
        }

        @Nullable
        @Override
        public Class<Directory> getType() {
            return Directory.class;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            // No dependencies
        }

        @Override
        public boolean isPresent() {
            return valueProvider == null || valueProvider.isPresent();
        }

        @Override
        public Directory getOrNull() {
            if (!isPresent()) {
                return null;
            }
            // TODO - factory should cache, and use a FixedDirectory instance when the value is fixed
            File dir = valueFactory.create();
            return new FixedDirectory(dir, resolver.newResolver(dir));
        }
    }

    private static class DefaultDirectoryVar extends DefaultPropertyState<Directory> implements DirectoryVar, TaskDependencyContainer {
        private final FileResolver resolver;

        DefaultDirectoryVar(FileResolver resolver) {
            super(Directory.class);
            this.resolver = resolver;
        }

        DefaultDirectoryVar(FileResolver resolver, Object value) {
            super(Directory.class);
            this.resolver = resolver;
            set(new ResolvingDirectory(resolver, value, null));
        }

        @Override
        public void setFromAnyValue(Object object) {
            if (object instanceof File) {
                File file = (File) object;
                set(file);
            } else {
                super.setFromAnyValue(object);
            }
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            if (getProvider() instanceof TaskDependencyContainer) {
                context.add(getProvider());
            }
        }

        @Override
        public FileTree getAsFileTree() {
            return resolver.resolveFilesAsTree(this);
        }

        @Override
        public Provider<File> getAsFile() {
            return new ToFileProvider(this);
        }

        void resolveAndSet(Object value) {
            set(new ResolvingDirectory(resolver, value, null));
        }

        @Override
        public void set(File dir) {
            File resolved = resolver.resolve(dir);
            set(new FixedDirectory(resolved, resolver.newResolver(resolved)));
        }

        @Override
        public Provider<Directory> dir(final String path) {
            return new AbstractMappingProvider<Directory, Directory>(Directory.class, this) {
                @Override
                protected Directory map(Directory dir) {
                    return dir.dir(path);
                }
            };
        }

        @Override
        public Provider<Directory> dir(final Provider<? extends CharSequence> path) {
            return new AbstractCombiningProvider<Directory, Directory, CharSequence>(Directory.class, this, path) {
                @Override
                protected Directory map(Directory b, CharSequence v) {
                    return b.dir(v.toString());
                }
            };
        }

        @Override
        public Provider<RegularFile> file(final String path) {
            return new AbstractMappingProvider<RegularFile, Directory>(RegularFile.class, this) {
                @Override
                protected RegularFile map(Directory dir) {
                    return dir.file(path);
                }
            };
        }

        @Override
        public Provider<RegularFile> file(final Provider<? extends CharSequence> path) {
            return new AbstractCombiningProvider<RegularFile, Directory, CharSequence>(RegularFile.class, this, path) {
                @Override
                protected RegularFile map(Directory b, CharSequence v) {
                    return b.file(v.toString());
                }
            };
        }
    }

    private static class BuildableDirectoryVar extends DefaultDirectoryVar {
        private final Task producer;

        BuildableDirectoryVar(FileResolver resolver, Task producer) {
            super(resolver);
            this.producer = producer;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(producer);
        }
    }

    private static class ToFileProvider extends AbstractMappingProvider<File, FileSystemLocation> {
        ToFileProvider(Provider<? extends FileSystemLocation> provider) {
            super(File.class, provider);
        }

        @Override
        protected File map(FileSystemLocation provider) {
            return provider.getAsFile();
        }
    }
}
