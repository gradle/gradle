/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.publish.gmm.internal;

import org.gradle.api.publish.PublicationArtifact;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.TaskProvider;

/**
 * A publication for which the gmm-publish plugin generates a Gradle Module Metadata file.
 */
public interface PublicationWithModuleMetadata<T extends PublicationArtifact> extends PublicationInternal<T> {

    /**
     * Sets the task that generates the Gradle Module Metadata file for this publication.
     */
    void setModuleDescriptorGenerator(TaskProvider<? extends GenerateModuleMetadata> moduleMetadataGenerator);
}
