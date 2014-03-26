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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.Versioned;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.resource.ResourceException;

import java.util.List;
import java.util.Set;

public interface VersionList {
    /**
     * <p>Adds those versions available for the given pattern.</p>
     *
     * If no versions are listed with the given pattern, then no versions are added.
     * 
     * @throws ResourceException If information for versions cannot be loaded.
     */
    void visit(ResourcePattern pattern, ModuleVersionArtifactMetaData artifact) throws ResourceException;

    Set<ListedVersion> getVersions();

    boolean isEmpty();

    List<ListedVersion> sortLatestFirst(LatestStrategy latestStrategy);

    class ListedVersion implements Versioned {
        private String version;
        private ResourcePattern pattern;

        public ListedVersion(String version, ResourcePattern pattern) {
            this.pattern = pattern;
            this.version = version;
        }

        public String getVersion() {
            return version;
        }

        /**
         * @return The pattern that can be used to load this version.
         */
        public ResourcePattern getPattern() {
            return pattern;
        }

        public boolean equals(Object other) {
            if (!(other instanceof ListedVersion)) {
                return false;
            }
            return version.equals(((ListedVersion) other).getVersion());
        }

        public int hashCode() {
            return version.hashCode();
        }
    }
}
