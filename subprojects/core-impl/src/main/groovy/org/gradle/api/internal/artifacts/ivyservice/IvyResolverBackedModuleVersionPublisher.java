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

import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.event.publish.EndArtifactPublishEvent;
import org.apache.ivy.core.event.publish.StartArtifactPublishEvent;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;

import java.io.File;
import java.io.IOException;
import java.util.Map;

class IvyResolverBackedModuleVersionPublisher implements ModuleVersionPublisher {
    private final EventManager eventManager;
    private final DependencyResolver resolver;

    IvyResolverBackedModuleVersionPublisher(EventManager eventManager, DependencyResolver resolver) {
        this.eventManager = eventManager;
        this.resolver = resolver;
    }

    public void publish(ModuleRevisionId id, Map<Artifact, File> artifacts) throws IOException {
        boolean successfullyPublished = false;
        try {
            resolver.beginPublishTransaction(id, true);
            // for each declared published artifact in this descriptor, do:
            for (Map.Entry<Artifact, File> entry : artifacts.entrySet()) {
                Artifact artifact = entry.getKey();
                File artifactFile = entry.getValue();
                publish(artifact, artifactFile);
            }
            resolver.commitPublishTransaction();
            successfullyPublished = true;
        } finally {
            if (!successfullyPublished) {
                resolver.abortPublishTransaction();
            }
        }
    }

    private void publish(Artifact artifact, File src) throws IOException {
        //notify triggers that an artifact is about to be published
        eventManager.fireIvyEvent(new StartArtifactPublishEvent(resolver, artifact, src, true));
        boolean successful = false; //set to true once the publish succeeds
        try {
            if (src.exists()) {
                resolver.publish(artifact, src, true);
                successful = true;
            }
        } finally {
            //notify triggers that the publish is finished, successfully or not.
            eventManager.fireIvyEvent(new EndArtifactPublishEvent(resolver, artifact, src, true, successful));
        }
    }
}
