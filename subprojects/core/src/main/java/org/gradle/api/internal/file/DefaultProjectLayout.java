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
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;

public class DefaultProjectLayout implements ProjectLayout {
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
    public Provider<RegularFile> file(final Provider<File> file) {
        return new AbstractProvider<RegularFile>() {
            @Override
            public boolean isPresent() {
                return file.isPresent();
            }

            @Override
            public RegularFile getOrNull() {
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
        private final PathToFileResolver fileResolver;

        FixedDirectory(File value, PathToFileResolver fileResolver) {
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

    private static class ResolvingFile extends AbstractProvider<RegularFile> {
        private final PathToFileResolver resolver;
        private final Provider<? extends CharSequence> path;

        ResolvingFile(PathToFileResolver resolver, Provider<? extends CharSequence> path) {
            this.resolver = resolver;
            this.path = path;
        }

        @Override
        public boolean isPresent() {
            return path.isPresent();
        }

        @Override
        public RegularFile getOrNull() {
            return new FixedFile(resolver.resolve(path));
        }
    }

    private static class DefaultRegularFileVar extends AbstractProvider<RegularFile> implements RegularFileVar {
        private final PathToFileResolver fileResolver;
        private RegularFile value;
        private Provider<? extends RegularFile> valueProvider;

        DefaultRegularFileVar(PathToFileResolver fileResolver) {
            this.fileResolver = fileResolver;
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

    private static class ResolvingDirectory extends AbstractProvider<Directory> {
        private final PathToFileResolver resolver;
        private final Provider<?> valueProvider;
        private final Factory<File> valueFactory;

        ResolvingDirectory(PathToFileResolver resolver, Object value, Provider<?> valueProvider) {
            this.resolver = resolver;
            this.valueProvider = valueProvider;
            this.valueFactory = resolver.resolveLater(value);
        }

        @Override
        public boolean isPresent() {
            return valueProvider == null || valueProvider.isPresent();
        }

        @Override
        public Directory getOrNull() {
            // TODO - factory should cache, and use a FixedDirectory instance when the value is fixed
            File dir = valueFactory.create();
            return new FixedDirectory(dir, resolver.newResolver(dir));
        }
    }

    private static class DefaultDirectoryVar extends AbstractProvider<Directory> implements DirectoryVar {
        private final PathToFileResolver resolver;
        private Directory value;
        private Provider<? extends Directory> valueProvider;

        DefaultDirectoryVar(PathToFileResolver resolver) {
            this.resolver = resolver;
        }

        DefaultDirectoryVar(PathToFileResolver resolver, Object value) {
            this.resolver = resolver;
            this.valueProvider = new ResolvingDirectory(resolver, value, null);
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
            if (valueProvider != null) {
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
            return new AbstractProvider<Directory>() {
                @Override
                public boolean isPresent() {
                    return DefaultDirectoryVar.this.isPresent();
                }

                @Override
                public Directory getOrNull() {
                    return DefaultDirectoryVar.this.get().dir(path);
                }
            };
        }

        @Override
        public Provider<Directory> dir(final Provider<? extends CharSequence> path) {
            return new AbstractProvider<Directory>() {
                @Override
                public boolean isPresent() {
                    return DefaultDirectoryVar.this.isPresent() && path.isPresent();
                }

                @Override
                public Directory getOrNull() {
                    return DefaultDirectoryVar.this.get().dir(path).get();
                }
            };
        }

        @Override
        public Provider<RegularFile> file(final String path) {
            return new AbstractProvider<RegularFile>() {
                @Override
                public boolean isPresent() {
                    return DefaultDirectoryVar.this.isPresent();
                }

                @Override
                public RegularFile getOrNull() {
                    return DefaultDirectoryVar.this.get().file(path);
                }
            };
        }

        @Override
        public Provider<RegularFile> file(final Provider<? extends CharSequence> path) {
            return new AbstractProvider<RegularFile>() {
                @Override
                public boolean isPresent() {
                    return DefaultDirectoryVar.this.isPresent() && path.isPresent();
                }

                @Override
                public RegularFile getOrNull() {
                    return DefaultDirectoryVar.this.get().file(path).get();
                }
            };
        }
    }

    private static class ToFileProvider extends AbstractProvider<File> {
        private final Provider<? extends Provider<File>> provider;

        ToFileProvider(Provider<? extends Provider<File>> provider) {
            this.provider = provider;
        }

        @Override
        public boolean isPresent() {
            return provider.isPresent();
        }

        @Override
        public File getOrNull() {
            return provider.get().get();
        }
    }
}
