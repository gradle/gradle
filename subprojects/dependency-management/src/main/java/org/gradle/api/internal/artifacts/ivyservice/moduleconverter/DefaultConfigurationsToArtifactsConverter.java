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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.local.model.MutableLocalComponentMetaData;
import org.gradle.util.GUtil;

import java.util.HashMap;
import java.util.Map;

public class DefaultConfigurationsToArtifactsConverter implements ConfigurationsToArtifactsConverter {

    public void addArtifacts(MutableLocalComponentMetaData metaData, Iterable<? extends Configuration> configurations) {
        ModuleVersionIdentifier id = metaData.getId();
        for (Configuration configuration : configurations) {
            for (PublishArtifact publishArtifact : configuration.getArtifacts()) {
                IvyArtifactName ivyArtifact = createIvyArtifact(publishArtifact, id);
                metaData.addArtifact(configuration.getName(), ivyArtifact, publishArtifact.getFile());
            }
        }
    }

    public IvyArtifactName createIvyArtifact(PublishArtifact publishArtifact, ModuleVersionIdentifier moduleVersionIdentifier) {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        if (GUtil.isTrue(publishArtifact.getClassifier())) {
            extraAttributes.put(Dependency.CLASSIFIER, publishArtifact.getClassifier());
        }
        String name = publishArtifact.getName();
        if (!GUtil.isTrue(name)) {
            name = moduleVersionIdentifier.getName();
        }
        return new DefaultIvyArtifactName(name, publishArtifact.getType(), publishArtifact.getExtension(), extraAttributes);
    }
}
