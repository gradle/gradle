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
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MavenVersionLister implements VersionLister {
    private final MavenMetadataLoader mavenMetadataLoader;

    public MavenVersionLister(CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor, FileStore<String> resourcesFileStore) {
        this.mavenMetadataLoader = new MavenMetadataLoader(cacheAwareExternalResourceAccessor, resourcesFileStore);
    }

    public VersionPatternVisitor newVisitor(final ModuleIdentifier module, final Collection<String> dest, final ResourceAwareResolveResult result) {
        return new VersionPatternVisitor() {
            final Set<ExternalResourceName> searched = new HashSet<ExternalResourceName>();

            public void visit(ResourcePattern pattern, IvyArtifactName artifact) throws ResourceException {
                if (isIncomplete(module)) {
                    return;
                }
                ExternalResourceName metadataLocation = pattern.toModulePath(module).resolve("maven-metadata.xml");
                if (!searched.add(metadataLocation)) {
                    return;
                }
                result.attempted(metadataLocation);
                MavenMetadata mavenMetaData = mavenMetadataLoader.load(metadataLocation);
                for (String version : mavenMetaData.versions) {
                    dest.add(version);
                }
            }
        };
    }

    private boolean isIncomplete(ModuleIdentifier module) {
        return module.getGroup().isEmpty() || module.getName().isEmpty();
    }
}
