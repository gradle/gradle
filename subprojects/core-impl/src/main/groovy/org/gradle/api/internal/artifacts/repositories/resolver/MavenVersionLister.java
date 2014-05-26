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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResourceAwareResolveResult;
import org.gradle.api.internal.artifacts.metadata.IvyArtifactName;
import org.gradle.internal.resource.ResourceException;
import org.gradle.internal.resource.transport.ExternalResourceRepository;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

public class MavenVersionLister implements VersionLister {
    private final MavenMetadataLoader mavenMetadataLoader;

    public MavenVersionLister(ExternalResourceRepository repository) {
        this.mavenMetadataLoader = new MavenMetadataLoader(repository);
    }

    public VersionList getVersionList(final ModuleIdentifier module, final ResourceAwareResolveResult result) {
        return new DefaultVersionList() {
            final Set<URI> searched = new HashSet<URI>();

            public void visit(ResourcePattern pattern, IvyArtifactName artifact) throws ResourceException {
                URI metadataLocation = pattern.toModulePath(module).resolve("maven-metadata.xml").getUri();
                if (!searched.add(metadataLocation)) {
                    return;
                }
                result.attempted(metadataLocation.toString());
                MavenMetadata mavenMetaData = mavenMetadataLoader.load(metadataLocation);
                for (String version : mavenMetaData.versions) {
                    add(version);
                }
            }
        };
    }
}