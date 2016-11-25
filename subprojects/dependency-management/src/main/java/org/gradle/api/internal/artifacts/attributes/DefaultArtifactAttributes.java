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

import com.google.common.io.Files;
import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.DefaultAttributeContainer;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

    public class DefaultArtifactAttributes {

    public static final Attribute<String> TYPE = attributeType("artifactType");
    public static final Attribute<String> EXTENSION = attributeType("artifactExtension");
    public static final Attribute<String> CLASSIFIER = attributeType("artifactClassifier");

    public static AttributeContainer forIvyArtifactName(IvyArtifactName ivyArtifactName) {
        return createAttributes(ivyArtifactName.getType(), ivyArtifactName.getExtension(), ivyArtifactName.getClassifier());
    }

    public static AttributeContainer forPublishArtifact(PublishArtifact artifact) {
        return createAttributes(artifact.getType(), artifact.getExtension(), artifact.getClassifier());
    }

    public static AttributeContainer forFile(File file) {
        String extension = Files.getFileExtension(file.getName());
        return createAttributes(extension, extension, "");
    }

    private static AttributeContainer createAttributes(String type, String extension, String classifier) {
        AttributeContainer attributes = new DefaultAttributeContainer();
        attributes.attribute(TYPE, type == null ? "" : type);
        attributes.attribute(EXTENSION, extension == null ? "" : extension);
        attributes.attribute(CLASSIFIER, classifier == null ? "" : classifier);
        return attributes;
    }

    private static Attribute<String> attributeType(String artifactName) {
        return Attribute.of(artifactName, String.class);
    }

}
