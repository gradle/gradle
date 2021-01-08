/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor;
import org.gradle.internal.Actions;

import java.util.function.Supplier;

class DefaultMavenRepositoryContentDescriptor extends DefaultRepositoryContentDescriptor implements MavenRepositoryContentDescriptor {
    private boolean snapshots = true;
    private boolean releases = true;

    public DefaultMavenRepositoryContentDescriptor(Supplier<String> repositoryNameSupplier) {
        super(repositoryNameSupplier);
    }

    @Override
    public void releasesOnly() {
        snapshots = false;
        releases = true;
    }

    @Override
    public void snapshotsOnly() {
        snapshots = true;
        releases = false;
    }

    @Override
    public Action<? super ArtifactResolutionDetails> toContentFilter() {
        Action<? super ArtifactResolutionDetails> filter = super.toContentFilter();
        if (!snapshots || !releases) {
            Action<? super ArtifactResolutionDetails> action = details -> {
                if (!details.isVersionListing()) {
                    String version = details.getComponentId().getVersion();
                    if (snapshots && !version.endsWith("-SNAPSHOT")) {
                        details.notFound();
                        return;
                    }
                    if (releases && version.endsWith("-SNAPSHOT")) {
                        details.notFound();
                    }
                }
            };
            if (filter == Actions.doNothing()) {
                return action;
            }
            return Actions.composite(filter, action);
        }
        return filter;
    }

    @Override
    public RepositoryContentDescriptorInternal asMutableCopy() {
        DefaultMavenRepositoryContentDescriptor copy = new DefaultMavenRepositoryContentDescriptor(getRepositoryNameSupplier());
        if (getIncludedConfigurations() != null) {
            copy.setIncludedConfigurations(Sets.newHashSet(getIncludedConfigurations()));
        }
        if (getExcludedConfigurations() != null) {
            copy.setExcludedConfigurations(Sets.newHashSet(getExcludedConfigurations()));
        }
        if (getIncludeSpecs() != null) {
            copy.setIncludeSpecs(Sets.newHashSet(getIncludeSpecs()));
        }
        if (getExcludeSpecs() != null) {
            copy.setExcludeSpecs(Sets.newHashSet(getExcludeSpecs()));
        }
        if (getRequiredAttributes() != null) {
            copy.setRequiredAttributes(Maps.newHashMap(getRequiredAttributes()));
        }
        return copy;
    }
}
