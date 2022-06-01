/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;

/**
 * Represents the 'name' part of an Ivy artifact, independent of which module version the artifact might belong to.
 */
public interface IvyArtifactName {
    String getName();

    String getType();

    @Nullable
    String getExtension();

    @Nullable
    String getClassifier();

    /**
     * Given a {@link PublishArtifact}, create an {@code IvyArtifactName} by extracting
     * the relevant information from the artifact. The returned {@code IvyArtifactName} is
     * lazily initialized, in that it will not attempt to retrieve any information from the
     * provided {@code publishArtifact} until information is fetched from the returned
     * {@code IvyArtifactName}. Thus, if the provided {@code publishArtifact} is backed
     * by a lazily defined entity, calling this method will not cause the backing entity
     * to become realized.
     *
     * @param publishArtifact The artifact to converert.
     *
     * @return A lazily initialized {@code IvyArtifactName}.
     */
    static IvyArtifactName lazyArtifactName(PublishArtifact publishArtifact) {
        // For some publish artifacts, for example LazyPublishArtifacts or those backed by ArchiveTasks, attempting to
        // fetch any information from the artifact may cause the underlying task to become realized.
        // So, attempt to further defer realization of the task for the cases where the returned IvyArtifactName is
        // never actually accessed.
        return new LazyIvyArtifactName(() -> {
            String name = publishArtifact.getName();
            if (name == null) {
                name = publishArtifact.getFile().getName();
            }
            String classifier = GUtil.elvis(publishArtifact.getClassifier(), null);
            return new DefaultIvyArtifactName(name, publishArtifact.getType(), publishArtifact.getExtension(), classifier);
        });
    }
}
