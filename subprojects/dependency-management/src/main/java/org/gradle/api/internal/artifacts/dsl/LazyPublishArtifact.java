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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;
import java.util.Date;

public class LazyPublishArtifact implements PublishArtifactInternal {
    private final ProviderInternal<?> provider;
    private final String version;
    private final FileResolver fileResolver;
    private PublishArtifactInternal delegate;

    /**
     * @deprecated Provide a {@link FileResolver} instead using {@link LazyPublishArtifact#LazyPublishArtifact(Provider, FileResolver)}.
     */
    @Deprecated
    public LazyPublishArtifact(Provider<?> provider) {
        this(provider, null);
        // TODO after Spring Boot resolves their usage of this constructor, uncomment this nag
        // https://github.com/spring-projects/spring-boot/issues/29074
        //        DeprecationLogger.deprecateInternalApi("constructor LazyPublishArtifact(Provider<?>)")
        //            .replaceWith("constructor LazyPublishArtifact(Provider<?>, FileResolver)")
        //            .willBeRemovedInGradle8()
        //            .withUpgradeGuideSection(7, "lazypublishartifact_fileresolver")
        //            .nagUser();
    }

    public LazyPublishArtifact(Provider<?> provider, FileResolver fileResolver) {
        this(provider, null, fileResolver);
    }

    public LazyPublishArtifact(Provider<?> provider, String version, FileResolver fileResolver) {
        // TODO after Spring Boot resolves their usage of this constructor, uncomment this nag
        // https://github.com/spring-projects/spring-boot/issues/29074
        //        DeprecationLogger.deprecateInternalApi("constructor LazyPublishArtifact(Provider<?>, FileResolver) or constructor LazyPublishArtifact(Provider<?>, String, FileResolver)"
        //        + " with a null FileResolver")
        //            .replaceWith("a non-null FileResolver")
        //            .willBeRemovedInGradle8()
        //            .withUpgradeGuideSection(7, "lazypublishartifact_fileresolver")
        //            .nagUser();
        this.provider = Providers.internal(provider);
        this.version = version;
        this.fileResolver = fileResolver;
    }

    @Override
    public String getName() {
        return getDelegate().getName();
    }

    @Override
    public String getExtension() {
        return getDelegate().getExtension();
    }

    @Override
    public String getType() {
        return getDelegate().getType();
    }

    @Override
    public String getClassifier() {
        return getDelegate().getClassifier();
    }

    @Override
    public File getFile() {
        return getDelegate().getFile();
    }

    @Override
    public Date getDate() {
        return new Date();
    }

    private PublishArtifactInternal getDelegate() {
        if (delegate == null) {
            Object value = provider.get();
            if (value instanceof FileSystemLocation) {
                FileSystemLocation location = (FileSystemLocation) value;
                delegate = fromFile(location.getAsFile());
            } else if (value instanceof File) {
                delegate = fromFile((File) value);
            } else if (value instanceof AbstractArchiveTask) {
                delegate = new ArchivePublishArtifact((AbstractArchiveTask) value);
            } else if (value instanceof Task) {
                delegate = fromFile(((Task) value).getOutputs().getFiles().getSingleFile());
            } else if (fileResolver != null) {
                delegate = fromFile(fileResolver.resolve(value));
            } else {
                throw new InvalidUserDataException(String.format("Cannot convert provided value (%s) to a file.", value));
            }
        }
        return delegate;
    }

    private DefaultPublishArtifact fromFile(File file) {
        ArtifactFile artifactFile = new ArtifactFile(file, version);
        return new DefaultPublishArtifact(artifactFile.getName(), artifactFile.getExtension(), artifactFile.getExtension(), artifactFile.getClassifier(), null, file);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(provider);
            }
        };
    }

    @Override
    public boolean shouldBePublished() {
        return getDelegate().shouldBePublished();
    }
}
