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

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.event.publish.EndArtifactPublishEvent;
import org.apache.ivy.core.event.publish.StartArtifactPublishEvent;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.MDArtifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.ConfigurationUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.DeprecationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultIvyDependencyPublisher implements IvyDependencyPublisher {
    public static final String FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE = "filePath";

    private static Logger logger = LoggerFactory.getLogger(DefaultIvyDependencyPublisher.class);

    public void publish(Set<String> configurations,
                        List<DependencyResolver> publishResolvers,
                        ModuleDescriptor moduleDescriptor,
                        File descriptorDestination,
                        EventManager eventManager) {
        try {
            Publication publication = new Publication(moduleDescriptor, eventManager, configurations, descriptorDestination);

            for (DependencyResolver resolver : publishResolvers) {
                logger.info("Publishing to repository {}", resolver);
                publication.publishTo(resolver);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class Publication {
        private final ModuleDescriptor moduleDescriptor;
        private final Set<String> configurations;
        private final File descriptorFile;

        private final EventManager eventManager;

        private Publication(ModuleDescriptor moduleDescriptor, EventManager eventManager, Set<String> configurations, File descriptorFile) {
            this.moduleDescriptor = moduleDescriptor;
            this.eventManager = eventManager;
            this.configurations = configurations;
            this.descriptorFile = descriptorFile;
        }

        public void publishTo(DependencyResolver resolver) throws IOException {
            Set<Artifact> allArtifacts = getAllArtifacts(moduleDescriptor);

            Map<Artifact, File> artifactsFiles = new LinkedHashMap<Artifact, File>();
            for (Artifact artifact : allArtifacts) {
                addPublishedArtifact(artifact, artifactsFiles);
            }
            if (descriptorFile != null) {
                addPublishedDescriptor(artifactsFiles);
            }

            boolean successfullyPublished = false;
            try {
                resolver.beginPublishTransaction(moduleDescriptor.getModuleRevisionId(), true);
                // for each declared published artifact in this descriptor, do:
                for (Map.Entry<Artifact, File> entry : artifactsFiles.entrySet()) {
                    Artifact artifact = entry.getKey();
                    File artifactFile = entry.getValue();
                    publish(artifact, artifactFile, resolver, true);
                }
                resolver.commitPublishTransaction();
                successfullyPublished = true;
            } finally {
                if (!successfullyPublished) {
                    resolver.abortPublishTransaction();
                }
            }
        }

        private void addPublishedDescriptor(Map<Artifact, File> artifactsFiles) {
            Artifact artifact = MDArtifact.newIvyArtifact(moduleDescriptor);
            checkArtifactFileExists(artifact, descriptorFile);
            artifactsFiles.put(artifact, descriptorFile);
        }

        private void addPublishedArtifact(Artifact artifact, Map<Artifact, File> artifactsFiles) {
            File artifactFile = new File(artifact.getExtraAttribute(FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE));
            checkArtifactFileExists(artifact, artifactFile);
            artifactsFiles.put(artifact, artifactFile);
        }

        private void checkArtifactFileExists(Artifact artifact, File artifactFile) {
            if (!artifactFile.exists()) {
                // TODO:DAZ This hack is required so that we don't log a warning when the Signing plugin is used. We need to allow conditional configurations so we can remove this.
                if (isSigningArtifact(artifact)) {
                    return;
                }
                String message = String.format("Attempted to publish an artifact '%s' that does not exist '%s'", artifact.getModuleRevisionId(), artifactFile);
                DeprecationLogger.nagUserOfDeprecatedBehaviour(message);
            }
        }

        private boolean isSigningArtifact(Artifact artifact) {
            return artifact.getType().endsWith(".asc") || artifact.getType().endsWith(".sig");
        }

        private Set<Artifact> getAllArtifacts(ModuleDescriptor moduleDescriptor) {
            Set<Artifact> allArtifacts = new LinkedHashSet<Artifact>();
            String[] trueConfigurations = ConfigurationUtils.replaceWildcards(configurations.toArray(new String[configurations.size()]), moduleDescriptor);
            for (String configuration : trueConfigurations) {
                Collections.addAll(allArtifacts, moduleDescriptor.getArtifacts(configuration));
            }
            return allArtifacts;
        }

        private void publish(Artifact artifact, File src,
                             DependencyResolver resolver, boolean overwrite) throws IOException {
            IvyContext.getContext().checkInterrupted();
            //notify triggers that an artifact is about to be published
            eventManager.fireIvyEvent(
                    new StartArtifactPublishEvent(resolver, artifact, src, overwrite));
            boolean successful = false; //set to true once the publish succeeds
            try {
                if (src.exists()) {
                    resolver.publish(artifact, src, overwrite);
                    successful = true;
                }
            } finally {
                //notify triggers that the publish is finished, successfully or not.
                eventManager.fireIvyEvent(
                        new EndArtifactPublishEvent(resolver, artifact, src, overwrite, successful));
            }
        }
    }

}
