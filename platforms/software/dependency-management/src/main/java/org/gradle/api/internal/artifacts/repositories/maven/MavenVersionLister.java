/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.maven;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.ExternalResourceName;

import java.util.List;
import java.util.Set;

public class MavenVersionLister {
    private final MavenMetadataLoader mavenMetadataLoader;

    public MavenVersionLister(MavenMetadataLoader mavenMetadataLoader) {
        this.mavenMetadataLoader = mavenMetadataLoader;
    }

    public void listVersions(ModuleIdentifier module, List<ResourcePattern> patterns, BuildableModuleVersionListingResolveResult result) {
        final Set<ExternalResourceName> searched = Sets.newHashSet();

        List<String> versions = Lists.newArrayList();
        boolean hasResult = false;
        for (ResourcePattern pattern : patterns) {
            ExternalResourceName metadataLocation = pattern.toModulePath(module).resolve("maven-metadata.xml");

            if (searched.add(metadataLocation)) {
                result.attempted(metadataLocation);
                try {
                    MavenMetadata mavenMetaData = mavenMetadataLoader.load(metadataLocation);
                    versions.addAll(mavenMetaData.versions);
                    hasResult = true;
                } catch (MissingResourceException e) {
                    // Continue
                }
            }
        }
        if (hasResult) {
            result.listed(versions);
        }
    }
}
