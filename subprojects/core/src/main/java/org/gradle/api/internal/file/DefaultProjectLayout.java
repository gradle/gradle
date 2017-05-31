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

import com.google.common.util.concurrent.Callables;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.provider.DefaultProvider;

import java.io.File;
import java.util.concurrent.Callable;

public class DefaultProjectLayout implements ProjectLayout {
    private final FixedDirectory projectDir;
    private final ResolvingCallable buildDirState;
    private final ResolvingDirectory buildDir;

    public DefaultProjectLayout(File projectDir, FileResolver resolver) {
        this.projectDir = new FixedDirectory(projectDir);
        this.buildDirState = new ResolvingCallable(resolver, Project.DEFAULT_BUILD_DIR_NAME);
        this.buildDir = new ResolvingDirectory(buildDirState);
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
        buildDirState.set(value);
    }

    private static class FixedDirectory extends DefaultProvider<File> implements Directory {
        FixedDirectory(File value) {
            super(Callables.returning(value));
        }
    }

    private static class ResolvingCallable implements Callable<File> {
        private final FileResolver resolver;
        private File cachedValue;
        private Object value;

        ResolvingCallable(final FileResolver resolver, final Object value) {
            this.resolver = resolver;
            this.value = value;
        }

        public void set(Object value) {
            this.value = value;
            cachedValue = null;
        }

        @Override
        public File call() throws Exception {
            if (cachedValue == null) {
                cachedValue = resolver.resolve(value);
            }
            return cachedValue;
        }
    }

    private static class ResolvingDirectory extends DefaultProvider<File> implements Directory {
        ResolvingDirectory(ResolvingCallable callable) {
            super(callable);
        }
    }
}
