/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.attributes;

import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.DefaultAttributeContainer;
import org.gradle.internal.component.model.IvyArtifactName;

public class DefaultArtifactAttributes {

    public static final String TYPE_ATTRIBUTE = "artifactType";
    public static final String EXTENSION_ATTRIBUTE = "artifactExtension";
    public static final String CLASSIFIER_ATTRIBUTE = "artifactClassifier";

    public static AttributeContainer forIvyArtifactName(IvyArtifactName ivyArtifactName) {
        return createAttributes(ivyArtifactName.getName(), ivyArtifactName.getType(), ivyArtifactName.getExtension(), ivyArtifactName.getClassifier());
    }

    public static AttributeContainer forPublishArtifact(PublishArtifact artifact) {
        return createAttributes(artifact.getName(), artifact.getType(), artifact.getExtension(), artifact.getClassifier());
    }

    private static AttributeContainer createAttributes(String name, String type, String extension, String classifier) {
        AttributeContainer attributes = new DefaultAttributeContainer();
        attributes.attribute(attributeType(TYPE_ATTRIBUTE), type);
        if (extension != null) {
            attributes.attribute(attributeType(EXTENSION_ATTRIBUTE), extension);
        }
        if (classifier != null) {
            attributes.attribute(attributeType(CLASSIFIER_ATTRIBUTE), classifier);
        }
        return attributes;
    }

    private static Attribute<String> attributeType(String artifactName) {
        return Attribute.of(artifactName, String.class);
    }

}
