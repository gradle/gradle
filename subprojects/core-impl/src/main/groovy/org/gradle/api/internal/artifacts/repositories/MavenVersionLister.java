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

package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.api.internal.resource.ResourceNotFoundException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MavenVersionLister implements VersionLister {
    private final MavenMetadataLoader mavenMetadataLoader;

    public MavenVersionLister(ExternalResourceRepository repository) {
        this.mavenMetadataLoader = new MavenMetadataLoader(repository);
    }

    public VersionList getVersionList(final ModuleRevisionId moduleRevisionId) {
        return new DefaultVersionList() {
            final Set<String> patterns = new HashSet<String>();

            @Override
            public void visit(String pattern, Artifact artifact) throws ResourceNotFoundException, ResourceException {
                if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
                    throw new InvalidUserDataException("Cannot locate maven-metadata.xml for non-maven layout");
                }
                if (!patterns.add(pattern)) {
                    return;
                }
                Map attributes = moduleRevisionId.getModuleId().getAttributes();
                String metaDataPattern = pattern.substring(0, pattern.length() - MavenPattern.M2_PER_MODULE_PATTERN.length()) + "maven-metadata.xml";
                String metadataLocation = IvyPatternHelper.substituteTokens(metaDataPattern, attributes);
                MavenMetadata mavenMetaData = mavenMetadataLoader.load(metadataLocation);
                add(mavenMetaData.versions);
            }
        };
    }
}