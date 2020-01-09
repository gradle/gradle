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

package org.gradle.api.internal.file;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.AbstractCombiningProvider;
import org.gradle.api.internal.provider.AbstractMappingProvider;
import org.gradle.api.internal.provider.AbstractReadOnlyProvider;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultFilePropertyFactory implements FilePropertyFactory, FileFactory {
    private final FileResolver fileResolver;
    private final FileCollectionFactory fileCollectionFactory;

    public DefaultFilePropertyFactory(FileResolver resolver, FileCollectionFactory fileCollectionFactory) {
        this.fileResolver = resolver;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public DirectoryProperty newDirectoryProperty() {
        return new DefaultDirectoryVar(fileResolver, fileCollectionFactory);
    }

    @Override
    public RegularFileProperty newFileProperty() {
        return new DefaultRegularFileVar(fileResolver);
    }

    @Override
    public Directory dir(File dir) {
        dir = fileResolver.resolve(dir);
        return new FixedDirectory(dir, fileResolver.newResolver(dir), fileCollectionFactory);
    }

    @Override
    public RegularFile file(File file) {
        file = fileResolver.resolve(file);
        return new FixedFile(file);
    }

    static class FixedDirectory extends DefaultFileSystemLocation implements Directory, Managed {
        final FileResolver fileResolver;
        private final FileCollectionFactory fileCollectionFactory;

        FixedDirectory(File value, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory) {
            super(value);
            this.fileResolver = fileResolver;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public boolean immutable() {
            return true;
        }

        @Override
        public Class<?> publicType() {
            return Directory.class;
        }

        @Override
        public Object unpackState() {
            return getAsFile();
        }

        @Override
        public int getFactoryId() {
            return ManagedFactories.DirectoryManagedFactory.FACTORY_ID;
        }

        @Override
        public Directory dir(String path) {
            File newDir = fileResolver.resolve(path);
            FileResolver dirResolver = fileResolver.newResolver(newDir);
            return new FixedDirectory(newDir, dirResolver, fileCollectionFactory.withResolver(dirResolver));
        }

        @Override
        public FileTree getAsFileTree() {
            return fileCollectionFactory.resolving(this).getAsFileTree();
        }

        @Override
        public Provider<Directory> dir(Provider<? extends CharSequence> path) {
            return new ResolvingDirectory(fileResolver, fileCollectionFactory, Providers.internal(path));
        }

        @Override
        public RegularFile file(String path) {
            return new FixedFile(fileResolver.resolve(path));
        }

        @Override
        public Provider<RegularFile> file(Provider<? extends CharSequence> path) {
            return new ResolvingRegularFileProvider(fileResolver, Providers.internal(path));
        }

        @Override
        public FileCollection files(Object... paths) {
            return fileCollectionFactory.withResolver(fileResolver).resolving(paths);
        }
    }

    static class FixedFile extends DefaultFileSystemLocation implements RegularFile, Managed {
        FixedFile(File file) {
            super(file);
        }

        @Override
        public boolean immutable() {
            return true;
        }

        @Override
        public Class<?> publicType() {
            return RegularFile.class;
        }

        @Override
        public Object unpackState() {
            return getAsFile();
        }

        @Override
        public int getFactoryId() {
            return ManagedFactories.RegularFileManagedFactory.FACTORY_ID;
        }
    }

    static class ResolvingRegularFileProvider extends AbstractMappingProvider<RegularFile, CharSequence> {
        private final PathToFileResolver resolver;

        ResolvingRegularFileProvider(PathToFileResolver resolver, ProviderInternal<? extends CharSequence> path) {
            super(RegularFile.class, path);
            this.resolver = resolver;
        }

        @Override
        protected RegularFile mapValue(CharSequence path) {
            return new FixedFile(resolver.resolve(path));
        }
    }

    static abstract class AbstractFileVar<T extends FileSystemLocation, THIS extends FileSystemLocationProperty<T>> extends DefaultProperty<T> implements FileSystemLocationProperty<T> {

        public AbstractFileVar(Class<T> type) {
            super(type);
        }

        protected abstract T fromFile(File file);

        @Override
        public Provider<File> getAsFile() {
            return new ToFileProvider(this);
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
        public THIS value(T value) {
            super.value(value);
            return Cast.uncheckedNonnullCast(this);
        }

        @Override
        public THIS value(Provider<? extends T> provider) {
            super.value(provider);
            return Cast.uncheckedNonnullCast(this);
        }

        @Override
        public void set(File file) {
            if (file == null) {
                set((T) null);
                return;
            }
            set(fromFile(file));
        }

        @Override
        public THIS fileValue(@Nullable File file) {
            set(file);
            return Cast.uncheckedNonnullCast(this);
        }

        @Override
        public THIS fileProvider(Provider<File> provider) {
            set(provider.map(file -> fromFile(file)));
            return Cast.uncheckedNonnullCast(this);
        }

        @Override
        public THIS convention(T value) {
            super.convention(value);
            return Cast.uncheckedNonnullCast(this);
        }

        @Override
        public THIS convention(Provider<? extends T> valueProvider) {
            super.convention(valueProvider);
            return Cast.uncheckedNonnullCast(this);
        }

        @Override
        public Provider<T> getLocationOnly() {
            return new AbstractReadOnlyProvider<T>() {
                @Nullable
                @Override
                public Class<T> getType() {
                    return AbstractFileVar.this.getType();
                }

                @Nullable
                @Override
                public T getOrNull() {
                    return AbstractFileVar.this.getOrNull();
                }
            };
        }
    }

    public static class DefaultRegularFileVar extends AbstractFileVar<RegularFile, RegularFileProperty> implements RegularFileProperty, Managed {
        private final PathToFileResolver fileResolver;

        DefaultRegularFileVar(PathToFileResolver fileResolver) {
            super(RegularFile.class);
            this.fileResolver = fileResolver;
        }

        @Override
        public Class<?> publicType() {
            return RegularFileProperty.class;
        }

        @Override
        public int getFactoryId() {
            return ManagedFactories.RegularFilePropertyManagedFactory.FACTORY_ID;
        }

        @Override
        protected RegularFile fromFile(File file) {
            return new FixedFile(fileResolver.resolve(file));
        }
    }

    static class ResolvingDirectory extends AbstractMappingProvider<Directory, CharSequence> {
        private final FileResolver resolver;
        private final FileCollectionFactory fileCollectionFactory;

        ResolvingDirectory(FileResolver resolver, FileCollectionFactory fileCollectionFactory, ProviderInternal<? extends CharSequence> valueProvider) {
            super(Directory.class, valueProvider);
            this.resolver = resolver;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        protected Directory mapValue(CharSequence path) {
            File dir = resolver.resolve(path);
            FileResolver dirResolver = this.resolver.newResolver(dir);
            return new FixedDirectory(dir, dirResolver, fileCollectionFactory.withResolver(dirResolver));
        }
    }

    public static class DefaultDirectoryVar extends AbstractFileVar<Directory, DirectoryProperty> implements DirectoryProperty, Managed {
        private final FileResolver resolver;
        private final FileCollectionFactory fileCollectionFactory;

        DefaultDirectoryVar(FileResolver resolver, FileCollectionFactory fileCollectionFactory) {
            super(Directory.class);
            this.resolver = resolver;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        DefaultDirectoryVar(FileResolver resolver, FileCollectionFactory fileCollectionFactory, Object value) {
            super(Directory.class);
            this.resolver = resolver;
            this.fileCollectionFactory = fileCollectionFactory;
            resolveAndSet(value);
        }

        @Override
        public Class<?> publicType() {
            return DirectoryProperty.class;
        }

        @Override
        public int getFactoryId() {
            return ManagedFactories.DirectoryPropertyManagedFactory.FACTORY_ID;
        }

        @Override
        public FileTree getAsFileTree() {
            return fileCollectionFactory.resolving(this).getAsFileTree();
        }

        void resolveAndSet(Object value) {
            File resolved = resolver.resolve(value);
            FileResolver dirResolver = resolver.newResolver(resolved);
            set(new FixedDirectory(resolved, dirResolver, fileCollectionFactory.withResolver(dirResolver)));
        }

        @Override
        protected Directory fromFile(File dir) {
            File resolved = resolver.resolve(dir);
            FileResolver dirResolver = resolver.newResolver(resolved);
            return new FixedDirectory(resolved, dirResolver, fileCollectionFactory.withResolver(dirResolver));
        }

        @Override
        public Provider<Directory> dir(final String path) {
            return new AbstractMappingProvider<Directory, Directory>(Directory.class, this) {
                @Override
                protected Directory mapValue(Directory dir) {
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
                protected RegularFile mapValue(Directory dir) {
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

        @Override
        public FileCollection files(Object... paths) {
            return fileCollectionFactory.withResolver(resolver).resolving(paths);
        }
    }

    static class ToFileProvider extends AbstractMappingProvider<File, FileSystemLocation> {
        ToFileProvider(ProviderInternal<? extends FileSystemLocation> provider) {
            super(File.class, provider);
        }

        @Override
        protected File mapValue(FileSystemLocation provider) {
            return provider.getAsFile();
        }
    }
}
