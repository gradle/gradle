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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

import static org.gradle.api.internal.artifacts.ArtifactAttributes.*;

public class DefaultArtifactAttributes {

    public static AttributeContainer forIvyArtifactName(IvyArtifactName ivyArtifactName, AttributeContainerInternal parentAttributes, ImmutableAttributesFactory attributesFactory) {
        return createAttributes(ivyArtifactName.getType(), ivyArtifactName.getExtension(), ivyArtifactName.getClassifier(), parentAttributes, attributesFactory);
    }

    public static AttributeContainer forFile(File file, ImmutableAttributesFactory attributesFactory) {
        String extension = Files.getFileExtension(file.getName());
        return createAttributes(extension, extension, "", ImmutableAttributes.EMPTY, attributesFactory);
    }

    private static AttributeContainer createAttributes(String type, String extension, String classifier, AttributeContainerInternal parentAttributes, ImmutableAttributesFactory attributesFactory) {
        return attributesFactory.builder(parentAttributes.asImmutable())
            .addAttribute(ARTIFACT_FORMAT, type == null ? "" : type)
            .addAttribute(ARTIFACT_EXTENSION, extension == null ? "" : extension)
            .addAttribute(ARTIFACT_CLASSIFIER, classifier == null ? "" : classifier).get();
    }
}
