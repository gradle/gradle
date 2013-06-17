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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.MDArtifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.util.ConfigurationUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.DefaultModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
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
                        List<ModuleVersionPublisher> publishResolvers,
                        ModuleDescriptor moduleDescriptor,
                        File descriptorDestination) {
        try {
            Publication publication = new Publication(moduleDescriptor, configurations, descriptorDestination);
            for (ModuleVersionPublisher publisher : publishResolvers) {
                logger.info("Publishing to {}", publisher);
                publication.publishTo(publisher);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class Publication {
        private final ModuleDescriptor moduleDescriptor;
        private final Set<String> configurations;
        private final File descriptorFile;

        private Publication(ModuleDescriptor moduleDescriptor, Set<String> configurations, File descriptorFile) {
            this.moduleDescriptor = moduleDescriptor;
            this.configurations = configurations;
            this.descriptorFile = descriptorFile;
        }

        public void publishTo(ModuleVersionPublisher publisher) throws IOException {
            Set<Artifact> allArtifacts = getAllArtifacts(moduleDescriptor);

            Map<Artifact, File> artifactsFiles = new LinkedHashMap<Artifact, File>();
            for (Artifact artifact : allArtifacts) {
                addPublishedArtifact(artifact, artifactsFiles);
            }
            if (descriptorFile != null) {
                addPublishedDescriptor(artifactsFiles);
            }

            publisher.publish(new DefaultModuleVersionPublishMetaData(moduleDescriptor.getModuleRevisionId(), artifactsFiles));
        }

        private void addPublishedDescriptor(Map<Artifact, File> artifactsFiles) {
            Artifact artifact = MDArtifact.newIvyArtifact(moduleDescriptor);
            if (checkArtifactFileExists(artifact, descriptorFile)) {
                artifactsFiles.put(artifact, descriptorFile);
            }
        }

        private void addPublishedArtifact(Artifact artifact, Map<Artifact, File> artifactsFiles) {
            File artifactFile = new File(artifact.getExtraAttribute(FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE));
            if (checkArtifactFileExists(artifact, artifactFile)) {
                artifactsFiles.put(artifact, artifactFile);
            }
        }

        private boolean checkArtifactFileExists(Artifact artifact, File artifactFile) {
            if (artifactFile.exists()) {
                return true;
            }
            // TODO:DAZ This hack is required so that we don't log a warning when the Signing plugin is used. We need to allow conditional configurations so we can remove this.
            if (!isSigningArtifact(artifact)) {
                String message = String.format("Attempted to publish an artifact '%s' that does not exist '%s'", artifact.getModuleRevisionId(), artifactFile);
                DeprecationLogger.nagUserOfDeprecatedBehaviour(message);
            }
            return false;
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
    }

}
