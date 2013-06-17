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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class IvyResolverBackedModuleVersionPublisher implements ModuleVersionPublisher {
    private final DependencyResolver resolver;

    public IvyResolverBackedModuleVersionPublisher(DependencyResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String toString() {
        return String.format("repository '%s'", resolver.getName());
    }

    public void setSettings(IvySettings settings) {
        settings.addResolver(resolver);
    }

    public void publish(ModuleVersionPublishMetaData moduleVersion) throws IOException {
        boolean successfullyPublished = false;
        try {
            resolver.beginPublishTransaction(moduleVersion.getId(), true);
            // for each declared published artifact in this descriptor, do:
            for (Map.Entry<Artifact, File> entry : moduleVersion.getArtifacts().entrySet()) {
                Artifact artifact = entry.getKey();
                File artifactFile = entry.getValue();
                resolver.publish(artifact, artifactFile, true);
            }
            resolver.commitPublishTransaction();
            successfullyPublished = true;
        } finally {
            if (!successfullyPublished) {
                resolver.abortPublishTransaction();
            }
        }
    }
}
