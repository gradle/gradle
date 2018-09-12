/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.AbstractCombiningProvider;
import org.gradle.api.internal.provider.AbstractMappingProvider;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.DefaultPropertyState;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultFilePropertyFactory implements FilePropertyFactory {
    private final FileResolver fileResolver;

    public DefaultFilePropertyFactory(FileResolver resolver) {
        this.fileResolver = resolver;
    }

    @Override
    public DirectoryProperty newDirectoryProperty() {
        return new DefaultDirectoryVar(fileResolver);
    }

    @Override
    public RegularFileProperty newFileProperty() {
        return new DefaultRegularFileVar(fileResolver);
    }

    static class FixedDirectory implements Directory, FileSystemLocation {
        private final File value;
        final FileResolver fileResolver;

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
            return new ResolvingDirectory(fileResolver, path);
        }

        @Override
        public RegularFile file(String path) {
            return new FixedFile(fileResolver.resolve(path));
        }

        @Override
        public Provider<RegularFile> file(Provider<? extends CharSequence> path) {
            return new ResolvingFile(fileResolver, Providers.internal(path));
        }
    }

    static class FixedFile implements RegularFile, FileSystemLocation {
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

    static class ResolvingFile extends AbstractMappingProvider<RegularFile, CharSequence> {
        private final PathToFileResolver resolver;

        ResolvingFile(PathToFileResolver resolver, ProviderInternal<? extends CharSequence> path) {
            super(RegularFile.class, path);
            this.resolver = resolver;
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            // No dependencies
            return true;
        }

        @Override
        protected RegularFile map(CharSequence path) {
            return new FixedFile(resolver.resolve(path));
        }
    }

    static class DefaultRegularFileVar extends DefaultPropertyState<RegularFile> implements RegularFileProperty, ProducerAwareProperty {
        private Task producer;
        private final PathToFileResolver fileResolver;

        DefaultRegularFileVar(PathToFileResolver fileResolver) {
            super(RegularFile.class);
            this.fileResolver = fileResolver;
        }

        @Override
        public void attachProducer(Task task) {
            if (this.producer != null && this.producer != task) {
                throw new IllegalStateException("This property already has a producer task associated with it.");
            }
            this.producer = task;
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            if (producer != null) {
                context.add(producer);
                return true;
            }
            return getProvider().maybeVisitBuildDependencies(context);
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

    static class ResolvingDirectory extends AbstractProvider<Directory> {
        private final FileResolver resolver;
        private final Provider<?> valueProvider;

        ResolvingDirectory(FileResolver resolver, Provider<?> valueProvider) {
            this.resolver = resolver;
            this.valueProvider = valueProvider;
        }

        @Nullable
        @Override
        public Class<Directory> getType() {
            return Directory.class;
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            // No dependencies
            return true;
        }

        @Override
        public boolean isPresent() {
            return valueProvider.isPresent();
        }

        @Override
        public Directory getOrNull() {
            if (!isPresent()) {
                return null;
            }
            File dir = resolver.resolve(valueProvider);
            return new FixedDirectory(dir, resolver.newResolver(dir));
        }

        @Override
        public String toString() {
            return String.format("provider(%s, %s)", getType(), valueProvider);
        }
    }

    static class DefaultDirectoryVar extends DefaultPropertyState<Directory> implements DirectoryProperty, ProducerAwareProperty {
        private final FileResolver resolver;
        private Task producer;

        DefaultDirectoryVar(FileResolver resolver) {
            super(Directory.class);
            this.resolver = resolver;
        }

        DefaultDirectoryVar(FileResolver resolver, Object value) {
            super(Directory.class);
            this.resolver = resolver;
            resolveAndSet(value);
        }

        @Override
        public void attachProducer(Task producer) {
            if (this.producer != null && this.producer != producer) {
                throw new IllegalStateException("This property already has a producer task associated with it.");
            }
            this.producer = producer;
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
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            if (producer != null) {
                context.add(producer);
                return true;
            }
            return getProvider().maybeVisitBuildDependencies(context);
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
            File resolved = resolver.resolve(value);
            set(new FixedDirectory(resolved, resolver.newResolver(resolved)));
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

    static class ToFileProvider extends AbstractMappingProvider<File, FileSystemLocation> {
        ToFileProvider(ProviderInternal<? extends FileSystemLocation> provider) {
            super(File.class, provider);
        }

        @Override
        protected File map(FileSystemLocation provider) {
            return provider.getAsFile();
        }
    }
}
