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

package org.gradle.api.publish.maven.internal.artifact;

import com.google.common.io.Files;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import javax.annotation.Nullable;
import java.io.File;

public class FileBasedMavenArtifact extends AbstractMavenArtifact {
    private final File file;
    private final String extension;
    @Nullable
    private final ProviderInternal<?> provider;

    public FileBasedMavenArtifact(File file, @Nullable ProviderInternal<?> provider) {
        this.file = file;
        extension = Files.getFileExtension(file.getName());
        this.provider = provider;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    protected String getDefaultExtension() {
        return extension;
    }

    @Override
    protected String getDefaultClassifier() {
        return null;
    }

    @Override
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                if (provider != null) {
                    provider.maybeVisitBuildDependencies(context);
                }
            }
        };
    }
}
