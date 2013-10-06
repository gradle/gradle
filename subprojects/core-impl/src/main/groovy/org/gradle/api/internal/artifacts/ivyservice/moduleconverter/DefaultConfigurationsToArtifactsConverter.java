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
import org.gradle.api.internal.artifacts.metadata.MutableLocalComponentMetaData;
import org.gradle.util.GUtil;

import java.util.HashMap;
import java.util.Map;

public class DefaultConfigurationsToArtifactsConverter implements ConfigurationsToArtifactsConverter {

    public void addArtifacts(MutableLocalComponentMetaData metaData, Iterable<? extends Configuration> configurations) {
        DefaultModuleDescriptor moduleDescriptor = metaData.getModuleDescriptor();
        for (Configuration configuration : configurations) {
            for (PublishArtifact publishArtifact : configuration.getArtifacts()) {
                Artifact ivyArtifact = createIvyArtifact(publishArtifact, moduleDescriptor.getModuleRevisionId());
                metaData.addArtifact(configuration.getName(), ivyArtifact, publishArtifact.getFile());
            }
        }
    }

    public Artifact createIvyArtifact(PublishArtifact publishArtifact, ModuleRevisionId moduleRevisionId) {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        if (GUtil.isTrue(publishArtifact.getClassifier())) {
            extraAttributes.put(Dependency.CLASSIFIER, publishArtifact.getClassifier());
        }
        String name = publishArtifact.getName();
        if (!GUtil.isTrue(name)) {
            name = moduleRevisionId.getName();
        }
        return new DefaultArtifact(
                moduleRevisionId,
                publishArtifact.getDate(),
                name,
                publishArtifact.getType(),
                publishArtifact.getExtension(),
                extraAttributes);
    }
}
