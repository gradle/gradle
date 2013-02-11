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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.Cast;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.util.GUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IvyPublisher {

    public void publish(IvyNormalizedPublication publication, IvyArtifactRepository repository) {
        DependencyResolver dependencyResolver = Cast.cast(ArtifactRepositoryInternal.class, repository).createResolver();
        ModuleRevisionId moduleRevisionId = IvyUtil.createModuleRevisionId(publication.getModule());

        try {

            for (IvyArtifact publishArtifact : publication.getArtifacts()) {
                Artifact ivyArtifact = createIvyArtifact(publishArtifact, moduleRevisionId);
                dependencyResolver.publish(ivyArtifact, publishArtifact.getFile(), true);
            }

            Artifact artifact = DefaultArtifact.newIvyArtifact(moduleRevisionId, null);
            dependencyResolver.publish(artifact, publication.getDescriptorFile(), true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Artifact createIvyArtifact(IvyArtifact ivyArtifact, ModuleRevisionId moduleRevisionId) {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        if (GUtil.isTrue(ivyArtifact.getClassifier())) {
            extraAttributes.put(Dependency.CLASSIFIER, ivyArtifact.getClassifier());
        }
        return new DefaultArtifact(
                moduleRevisionId,
                null,
                ivyArtifact.getName(),
                ivyArtifact.getType(),
                ivyArtifact.getExtension(),
                extraAttributes);
    }
}
