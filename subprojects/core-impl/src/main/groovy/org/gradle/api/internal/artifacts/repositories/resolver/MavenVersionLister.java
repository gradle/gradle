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
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.api.internal.resource.ResourceException;

import java.util.HashSet;
import java.util.Set;

public class MavenVersionLister implements VersionLister {
    private final MavenMetadataLoader mavenMetadataLoader;

    public MavenVersionLister(ExternalResourceRepository repository) {
        this.mavenMetadataLoader = new MavenMetadataLoader(repository);
    }

    public VersionList getVersionList(final ModuleIdentifier module) {
        return new DefaultVersionList() {
            final Set<String> searched = new HashSet<String>();

            public void visit(ResourcePattern resourcePattern, ModuleVersionArtifactMetaData artifact) throws ResourceException {
                String metadataLocation = resourcePattern.toModulePath(module) + "/maven-metadata.xml";
                if (!searched.add(metadataLocation)) {
                    return;
                }
                MavenMetadata mavenMetaData = mavenMetadataLoader.load(metadataLocation);
                for (String version : mavenMetaData.versions) {
                    add(new ListedVersion(version, resourcePattern));
                }
            }
        };
    }
}