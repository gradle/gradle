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
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;

public class DefaultProjectLayout implements ProjectLayout {
    private final FixedDirectory projectDir;
    private final ResolvingDirectory buildDir;

    public DefaultProjectLayout(File projectDir, FileResolver resolver) {
        this.projectDir = new FixedDirectory(projectDir, resolver);
        this.buildDir = new ResolvingDirectory(resolver, Project.DEFAULT_BUILD_DIR_NAME);
    }

    @Override
    public Directory getProjectDirectory() {
        return projectDir;
    }

    @Override
    public Directory getBuildDirectory() {
        return buildDir;
    }

    public void setBuildDirectory(Object value) {
        buildDir.set(value);
    }

    private static class FixedDirectory extends AbstractProvider<File> implements Directory {
        private final File value;
        private final PathToFileResolver fileResolver;

        FixedDirectory(File value, PathToFileResolver fileResolver) {
            this.value = value;
            this.fileResolver = fileResolver;
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
        public Directory dir(Provider<? extends CharSequence> path) {
            return new ResolvingDirectory(fileResolver, path);
        }

        @Override
        public Provider<File> file(String path) {
            // Good enough for now
            return dir(path);
        }

        @Override
        public Provider<File> file(Provider<? extends CharSequence> path) {
            // Good enough for now
            return dir(path);
        }
    }

    private static class ResolvingDirectory extends AbstractProvider<File> implements Directory, Factory<File> {
        private final PathToFileResolver resolver;
        private Factory<File> value;

        ResolvingDirectory(PathToFileResolver resolver, Object value) {
            this.resolver = resolver;
            this.value = resolver.resolveLater(value);
        }

        @Override
        public File create() {
            return get();
        }

        @Override
        public File getOrNull() {
            // Let the resolver decide whether the value should be cached or not
            return value.create();
        }

        public void set(Object value) {
            this.value = resolver.resolveLater(value);
        }

        @Override
        public Directory dir(String path) {
            return new ResolvingDirectory(resolver.newResolver(this), path);
        }

        @Override
        public Directory dir(Provider<? extends CharSequence> path) {
            return new ResolvingDirectory(resolver.newResolver(this), path);
        }

        @Override
        public Provider<File> file(String path) {
            // Good enough for now
            return dir(path);
        }

        @Override
        public Provider<File> file(Provider<? extends CharSequence> path) {
            // Good enough for now
            return dir(path);
        }
    }
}
