/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.BuildableModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyPublisher;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactsToModuleDescriptorConverter implements ArtifactsToModuleDescriptorConverter {
    public final static ArtifactsExtraAttributesStrategy IVY_FILE_STRATEGY = new ArtifactsExtraAttributesStrategy() {
        public Map<String, String> createExtraAttributes(PublishArtifact publishArtifact) {
            return new HashMap<String, String>();
        }
    };

    public final static ArtifactsExtraAttributesStrategy RESOLVE_STRATEGY = new ArtifactsExtraAttributesStrategy() {
        public Map<String, String> createExtraAttributes(PublishArtifact publishArtifact) {
            return WrapUtil.toMap(DefaultIvyDependencyPublisher.FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE, publishArtifact.getFile().getAbsolutePath());
        }
    };

    private final ArtifactsExtraAttributesStrategy artifactsExtraAttributesStrategy;

    public DefaultArtifactsToModuleDescriptorConverter(ArtifactsExtraAttributesStrategy artifactsExtraAttributesStrategy) {
        this.artifactsExtraAttributesStrategy = artifactsExtraAttributesStrategy;
    }

    public void addArtifacts(BuildableModuleVersionPublishMetaData metaData, Iterable<? extends Configuration> configurations) {
        DefaultModuleDescriptor moduleDescriptor = (DefaultModuleDescriptor) metaData.getModuleDescriptor();
        for (Configuration configuration : configurations) {
            for (PublishArtifact publishArtifact : configuration.getArtifacts()) {
                Artifact ivyArtifact = createIvyArtifact(publishArtifact, moduleDescriptor.getModuleRevisionId());
                moduleDescriptor.addArtifact(configuration.getName(), ivyArtifact);
                metaData.addArtifact(ivyArtifact, publishArtifact.getFile());
            }
        }
    }

    public Artifact createIvyArtifact(PublishArtifact publishArtifact, ModuleRevisionId moduleRevisionId) {
        Map<String, String> extraAttributes = artifactsExtraAttributesStrategy.createExtraAttributes(publishArtifact);
        if (GUtil.isTrue(publishArtifact.getClassifier())) {
            extraAttributes.put(Dependency.CLASSIFIER, publishArtifact.getClassifier());
        }
        return new DefaultArtifact(
                moduleRevisionId,
                publishArtifact.getDate(),
                publishArtifact.getName(),
                publishArtifact.getType(),
                publishArtifact.getExtension(),
                extraAttributes);
    }
}
