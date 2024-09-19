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

package org.gradle.api.publish.ivy.internal.publisher;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver;
import org.gradle.api.internal.artifacts.repositories.transport.NetworkOperationBackOffAndRetry;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.concurrent.Callable;

public class DependencyResolverIvyPublisher implements IvyPublisher {
    private final NetworkOperationBackOffAndRetry<Void> networkOperationBackOffAndRetry = new NetworkOperationBackOffAndRetry<>();

    @Override
    public void publish(IvyNormalizedPublication publication, IvyArtifactRepository repository) {
        IvyResolver publisher = ((DefaultIvyArtifactRepository) repository).createPublisher();
        ModuleComponentIdentifier moduleVersionIdentifier = DefaultModuleComponentIdentifier.newId(publication.getCoordinates());

        for (NormalizedIvyArtifact artifact : publication.getAllArtifacts()) {
            ModuleComponentArtifactMetadata artifactMetadata = new DefaultModuleComponentArtifactMetadata(moduleVersionIdentifier, createIvyArtifact(artifact));
            publish(publisher, artifact, artifactMetadata);
        }
    }

    private void publish(IvyResolver publisher, NormalizedIvyArtifact artifact, ModuleComponentArtifactMetadata artifactMetadata) {
        networkOperationBackOffAndRetry.withBackoffAndRetry(new Callable<Void>() {
            @Override
            public Void call() {
                publisher.publish(artifactMetadata, artifact.getFile());
                return null;
            }

            @Override
            public String toString() {
                return "Publish " + artifactMetadata;
            }

        });
    }

    private IvyArtifactName createIvyArtifact(NormalizedIvyArtifact artifact) {
        return new DefaultIvyArtifactName(artifact.getName(), artifact.getType(), artifact.getExtension(), artifact.getClassifier());
    }
}
