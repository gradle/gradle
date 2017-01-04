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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.transform.ArtifactTransformRegistrations;
import org.gradle.api.attributes.AttributeContainer;

import java.io.File;
import java.util.List;

public interface ArtifactTransformRegistrationsInternal extends ArtifactTransformRegistrations {

    boolean areMatchingAttributes(AttributeContainer artifact, AttributeContainer target);

    Transformer<List<File>, File> getTransform(AttributeContainer artifact, AttributeContainer target);

    List<ResolvedArtifact> getTransformedArtifacts(ResolvedArtifact artifact, AttributeContainer target);

    void putTransformedArtifact(ResolvedArtifact artifact, AttributeContainer target, List<ResolvedArtifact> transformResults);

    List<File> getTransformedFile(File file, AttributeContainer target);

    void putTransformedFile(File file, AttributeContainer target, List<File> transformResults);
}
