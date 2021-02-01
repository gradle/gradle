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

import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.AbstractCombiningProvider;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.MappingProvider;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;
import java.io.File;

@ServiceScope(Scope.Global.class)
public class DefaultFilePropertyFactory implements FilePropertyFactory, FileFactory {
    private final PropertyHost host;
    private final FileResolver fileResolver;
    private final FileCollectionFactory fileCollectionFactory;

    public DefaultFilePropertyFactory(PropertyHost host, FileResolver resolver, FileCollectionFactory fileCollectionFactory) {
        this.host = host;
        this.fileResolver = resolver;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public DirectoryProperty newDirectoryProperty() {
        return new DefaultDirectoryVar(host, fileResolver, fileCollectionFactory);
    }

    @Override
    public RegularFileProperty newFileProperty() {
        return new DefaultRegularFileVar(host, fileResolver);
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

    private static class FixedDirectory extends DefaultFileSystemLocation implements Directory, Managed {
        final FileResolver fileResolver;
        private final FileCollectionFactory fileCollectionFactory;

        FixedDirectory(File value, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory) {
            super(value);
            this.fileResolver = fileResolver;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public boolean isImmutable() {
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
            return new MappingProvider<Directory, CharSequence>(Directory.class, Providers.internal(path), new ResolvingDirectoryTransformer(fileResolver, fileCollectionFactory));
        }

        @Override
        public RegularFile file(String path) {
            return new FixedFile(fileResolver.resolve(path));
        }

        @Override
        public Provider<RegularFile> file(Provider<? extends CharSequence> path) {
            return new MappingProvider<RegularFile, CharSequence>(RegularFile.class, Providers.internal(path), new ResolvingRegularFileTransform(fileResolver));
        }

        @Override
        public FileCollection files(Object... paths) {
            return fileCollectionFactory.withResolver(fileResolver).resolving(paths);
        }
    }

    private static class FixedFile extends DefaultFileSystemLocation implements RegularFile, Managed {
        FixedFile(File file) {
            super(file);
        }

        @Override
        public boolean isImmutable() {
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

    private static class ResolvingRegularFileTransform implements Transformer<RegularFile, CharSequence> {
        private final PathToFileResolver resolver;

        ResolvingRegularFileTransform(PathToFileResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public RegularFile transform(CharSequence path) {
            return new FixedFile(resolver.resolve(path));
        }
    }

    private static abstract class AbstractFileVar<T extends FileSystemLocation, THIS extends FileSystemLocationProperty<T>> extends DefaultProperty<T> implements FileSystemLocationProperty<T> {

        public AbstractFileVar(PropertyHost host, Class<T> type) {
            super(host, type);
        }

        protected abstract T fromFile(File file);

        @Override
        public Provider<File> getAsFile() {
            return new MappingProvider<>(File.class, this, new ToFileTransformer());
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
            set(provider.map(new Transformer<T, File>() {
                @Override
                public T transform(File file) {
                    return fromFile(file);
                }
            }));
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
            return new AbstractMinimalProvider<T>() {
                @Nullable
                @Override
                public Class<T> getType() {
                    return AbstractFileVar.this.getType();
                }

                @Override
                protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
                    return calculateOwnValueNoProducer(consumer);
                }
            };
        }
    }

    public static class DefaultRegularFileVar extends AbstractFileVar<RegularFile, RegularFileProperty> implements RegularFileProperty, Managed {
        private final PathToFileResolver fileResolver;

        DefaultRegularFileVar(PropertyHost host, PathToFileResolver fileResolver) {
            super(host, RegularFile.class);
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

    private static class ResolvingDirectoryTransformer implements Transformer<Directory, CharSequence> {
        private final FileResolver resolver;
        private final FileCollectionFactory fileCollectionFactory;

        ResolvingDirectoryTransformer(FileResolver resolver, FileCollectionFactory fileCollectionFactory) {
            this.resolver = resolver;
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Override
        public Directory transform(CharSequence path) {
            File dir = resolver.resolve(path);
            FileResolver dirResolver = this.resolver.newResolver(dir);
            return new FixedDirectory(dir, dirResolver, fileCollectionFactory.withResolver(dirResolver));
        }
    }

    public static class DefaultDirectoryVar extends AbstractFileVar<Directory, DirectoryProperty> implements DirectoryProperty, Managed {
        private final FileResolver resolver;
        private final FileCollectionFactory fileCollectionFactory;

        DefaultDirectoryVar(PropertyHost host, FileResolver resolver, FileCollectionFactory fileCollectionFactory) {
            super(host, Directory.class);
            this.resolver = resolver;
            this.fileCollectionFactory = fileCollectionFactory;
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

        @Override
        protected Directory fromFile(File dir) {
            File resolved = resolver.resolve(dir);
            FileResolver dirResolver = resolver.newResolver(resolved);
            return new FixedDirectory(resolved, dirResolver, fileCollectionFactory.withResolver(dirResolver));
        }

        @Override
        public Provider<Directory> dir(final String path) {
            return new MappingProvider<>(Directory.class, this, new PathToDirectoryTransformer(path));
        }

        @Override
        public Provider<Directory> dir(final Provider<? extends CharSequence> path) {
            return new AbstractCombiningProvider<Directory, Directory, CharSequence>(Directory.class, this, Providers.internal(path)) {
                @Override
                protected Directory map(Directory b, CharSequence v) {
                    return b.dir(v.toString());
                }
            };
        }

        @Override
        public Provider<RegularFile> file(final String path) {
            return new MappingProvider<>(RegularFile.class, this, new PathToFileTransformer(path));
        }

        @Override
        public Provider<RegularFile> file(final Provider<? extends CharSequence> path) {
            return new AbstractCombiningProvider<RegularFile, Directory, CharSequence>(RegularFile.class, this, Providers.internal(path)) {
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

    private static class PathToFileTransformer implements Transformer<RegularFile, Directory> {
        private final String path;

        public PathToFileTransformer(String path) {
            this.path = path;
        }

        @Override
        public RegularFile transform(Directory directory) {
            return directory.file(path);
        }
    }

    private static class PathToDirectoryTransformer implements Transformer<Directory, Directory> {
        private final String path;

        public PathToDirectoryTransformer(String path) {
            this.path = path;
        }

        @Override
        public Directory transform(Directory directory) {
            return directory.dir(path);
        }
    }

    private static class ToFileTransformer implements Transformer<File, FileSystemLocation> {
        @Override
        public File transform(FileSystemLocation location) {
            return location.getAsFile();
        }
    }
}
