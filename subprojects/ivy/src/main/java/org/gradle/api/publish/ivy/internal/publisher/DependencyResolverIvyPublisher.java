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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.util.GUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DependencyResolverIvyPublisher implements IvyPublisher {

    public void publish(IvyNormalizedPublication publication, PublicationAwareRepository repository) {
        ModuleVersionPublisher publisher = repository.createPublisher();
        IvyPublicationIdentity projectIdentity = publication.getProjectIdentity();
        ModuleRevisionId moduleRevisionId = IvyUtil.createModuleRevisionId(projectIdentity.getOrganisation(), projectIdentity.getModule(), projectIdentity.getRevision());
        ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(moduleRevisionId);
        DefaultModuleVersionPublishMetaData publishMetaData = new DefaultModuleVersionPublishMetaData(moduleVersionIdentifier);

        try {
            for (IvyArtifact publishArtifact : publication.getArtifacts()) {
                Artifact ivyArtifact = createIvyArtifact(publishArtifact, moduleRevisionId);
                publishMetaData.addArtifact(ivyArtifact, publishArtifact.getFile());
            }

            Artifact artifact = DefaultArtifact.newIvyArtifact(moduleRevisionId, null);
            publishMetaData.addArtifact(artifact, publication.getDescriptorFile());

            publisher.publish(publishMetaData);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Artifact createIvyArtifact(IvyArtifact ivyArtifact, ModuleRevisionId moduleRevisionId) {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        if (GUtil.isTrue(ivyArtifact.getClassifier())) {
            extraAttributes.put(Dependency.CLASSIFIER, ivyArtifact.getClassifier());
        }
        return new DefaultArtifact(
                moduleRevisionId,
                null,
                GUtil.elvis(ivyArtifact.getName(), moduleRevisionId.getName()),
                ivyArtifact.getType(),
                ivyArtifact.getExtension(),
                extraAttributes);
    }
}
