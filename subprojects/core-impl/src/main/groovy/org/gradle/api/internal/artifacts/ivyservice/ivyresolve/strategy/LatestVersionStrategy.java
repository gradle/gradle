/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.Versioned;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LatestVersionStrategy implements LatestStrategy {
    private final VersionMatcher versionMatcher;

    public LatestVersionStrategy(VersionMatcher versionMatcher) {
        this.versionMatcher = versionMatcher;
    }

    public <T extends Versioned> List<T> sort(Collection<T> versions) {
        return CollectionUtils.sort(versions, this);
    }

    public <T extends Versioned> T findLatest(Collection<T> elements) {
        return Collections.max(elements, this);
    }

    // TODO: Compared to Ivy's LatestRevisionStrategy.compare(), this method doesn't do any
    // equivalent of ModuleRevisionId.normalizeRevision(). Should we add this functionality
    // back in, either here or (probably better) in (Chain)VersionMatcher?
    public int compare(Versioned element1, Versioned element2) {
        String version1 = element1.getVersion();
        String version2 = element2.getVersion();

        /*
         * The revisions can still be not resolved, so we use the current version matcher to
         * know if one revision is dynamic, and in this case if it should be considered greater
         * or lower than the other one. Note that if the version matcher compare method returns
         * 0, it's because it's not possible to know which revision is greater. In this case we
         * consider the dynamic one to be greater, because most of the time it will then be
         * actually resolved and a real comparison will occur.
         */
        if (versionMatcher.isDynamic(version1)) {
            int c = versionMatcher.compare(version1, version2);
            return c >= 0 ? 1 : -1;
        } else if (versionMatcher.isDynamic(version2)) {
            int c = versionMatcher.compare(version2, version1);
            return c >= 0 ? -1 : 1;
        }

        return versionMatcher.compare(version1, version2);
    }
}
