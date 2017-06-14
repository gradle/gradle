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
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.internal.provider.AbstractCombiningProvider;
import org.gradle.api.internal.provider.AbstractMappingProvider;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

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
    public DirectoryVar getBuildDirectory() {
        return buildDir;
    }

    @Override
    public DirectoryVar newDirectoryVar() {
        return new DefaultDirectoryVar(projectDir.fileResolver);
    }

    @Override
    public RegularFileVar newFileVar() {
        return new DefaultRegularFileVar(projectDir.fileResolver);
    }

    @Override
    public DirectoryVar newOutputDirectory(Task producer) {
        return new BuildableDirectoryVar(projectDir.fileResolver, producer);
    }

    @Override
    public RegularFileVar newOutputFile(Task producer) {
        return new BuildableRegularFileVar(projectDir.fileResolver, producer);
    }

    @Override
    public RegularFileVar newInputFile(final Task consumer) {
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
    public Provider<RegularFile> file(Provider<File> provider) {
        return new AbstractMappingProvider<RegularFile, File>(provider) {
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

    private static class FixedDirectory extends AbstractProvider<File> implements Directory {
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
        public File getOrNull() {
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

    private static class FixedFile extends AbstractProvider<File> implements RegularFile {
        private final File file;

        FixedFile(File file) {
            this.file = file;
        }

        @Override
        public String toString() {
            return file.toString();
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public File getOrNull() {
            return file;
        }
    }

    private static class ResolvingFile extends AbstractMappingProvider<RegularFile, CharSequence> implements TaskDependencyContainer {
        private final PathToFileResolver resolver;

        ResolvingFile(PathToFileResolver resolver, Provider<? extends CharSequence> path) {
            super(path);
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

    private static class DefaultRegularFileVar extends AbstractProvider<RegularFile> implements RegularFileVar, TaskDependencyContainer {
        private final PathToFileResolver fileResolver;
        private RegularFile value;
        private Provider<? extends RegularFile> valueProvider;

        DefaultRegularFileVar(PathToFileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            if (valueProvider != null) {
                context.add(valueProvider);
            }
        }

        @Override
        public boolean isPresent() {
            return value != null || valueProvider != null;
        }

        @Override
        public RegularFile getOrNull() {
            if (value != null) {
                return value;
            }
            if (valueProvider != null) {
                return valueProvider.getOrNull();
            }
            return null;
        }

        @Override
        public Provider<File> getAsFile() {
            return new ToFileProvider(this);
        }

        @Override
        public void set(File file) {
            this.value = new FixedFile(fileResolver.resolve(file));
            this.valueProvider = null;
        }

        @Override
        public void set(final Provider<? extends RegularFile> provider) {
            this.value = null;
            this.valueProvider = provider;
        }

        @Override
        public void set(RegularFile value) {
            this.value = value;
            this.valueProvider = null;
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

    private static class DefaultDirectoryVar extends AbstractProvider<Directory> implements DirectoryVar, TaskDependencyContainer {
        private final FileResolver resolver;
        private Directory value;
        private Provider<? extends Directory> valueProvider;

        DefaultDirectoryVar(FileResolver resolver) {
            this.resolver = resolver;
        }

        DefaultDirectoryVar(FileResolver resolver, Object value) {
            this.resolver = resolver;
            this.valueProvider = new ResolvingDirectory(resolver, value, null);
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            if (valueProvider != null) {
                context.add(valueProvider);
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

        @Override
        public boolean isPresent() {
            return value != null || valueProvider != null;
        }

        @Override
        public Directory getOrNull() {
            if (value != null) {
                return value;
            }
            if (valueProvider != null && valueProvider.isPresent()) {
                return valueProvider.get();
            }
            return null;
        }

        void resolveAndSet(Object value) {
            this.value = null;
            this.valueProvider = new ResolvingDirectory(resolver, value, null);
        }

        @Override
        public void set(File dir) {
            File resolved = resolver.resolve(dir);
            this.value = new FixedDirectory(resolved, resolver.newResolver(resolved));
            this.valueProvider = null;
        }

        @Override
        public void set(Directory value) {
            this.value = value;
            this.valueProvider = null;
        }

        @Override
        public void set(Provider<? extends Directory> provider) {
            this.value = null;
            this.valueProvider = provider;
        }

        @Override
        public Provider<Directory> dir(final String path) {
            return new AbstractMappingProvider<Directory, Directory>(this) {
                @Override
                protected Directory map(Directory dir) {
                    return dir.dir(path);
                }
            };
        }

        @Override
        public Provider<Directory> dir(final Provider<? extends CharSequence> path) {
            return new AbstractCombiningProvider<Directory, Directory, CharSequence>(this, path) {
                @Override
                protected Directory map(Directory b, CharSequence v) {
                    return b.dir(v.toString());
                }
            };
        }

        @Override
        public Provider<RegularFile> file(final String path) {
            return new AbstractMappingProvider<RegularFile, Directory>(this) {
                @Override
                protected RegularFile map(Directory dir) {
                    return dir.file(path);
                }
            };
        }

        @Override
        public Provider<RegularFile> file(final Provider<? extends CharSequence> path) {
            return new AbstractCombiningProvider<RegularFile, Directory, CharSequence>(this, path) {
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

    private static class ToFileProvider extends AbstractMappingProvider<File, Provider<File>> {
        ToFileProvider(Provider<? extends Provider<File>> provider) {
            super(provider);
        }

        @Override
        protected File map(Provider<File> provider) {
            return provider.get();
        }
    }
}
