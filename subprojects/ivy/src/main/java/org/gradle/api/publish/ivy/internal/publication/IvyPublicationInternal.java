/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.Task;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.api.tasks.TaskProvider;

import java.util.Set;

public interface IvyPublicationInternal extends IvyPublication, PublicationInternal<IvyArtifact> {

    IvyPublicationIdentity getIdentity();

    @Override
    IvyModuleDescriptorSpecInternal getDescriptor();

    void setIvyDescriptorGenerator(TaskProvider<? extends Task> descriptorGenerator);

    void setModuleDescriptorGenerator(TaskProvider<? extends Task> descriptorGenerator);

    IvyNormalizedPublication asNormalisedPublication();

    boolean writeGradleMetadataMarker();
}
