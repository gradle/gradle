/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.XmlTransformer;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;

import java.io.File;

public class ErrorHandlingArtifactPublisher implements ArtifactPublisher {
    private final ArtifactPublisher artifactPublisher;

    public ErrorHandlingArtifactPublisher(ArtifactPublisher artifactPublisher) {
        this.artifactPublisher = artifactPublisher;
    }

    public void publish(Module module, ConfigurationInternal configuration, File descriptorDestination, XmlTransformer descriptorModifier) {
        try {
            artifactPublisher.publish(module, configuration, descriptorDestination, descriptorModifier);
        } catch (Throwable e) {
            throw new PublishException(String.format("Could not publish %s.", configuration), e);
        }
    }
}
