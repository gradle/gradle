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

package org.gradle.cache.internal;

import com.google.common.collect.Sets;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.util.GradleVersion;

import java.util.SortedSet;

public class UsedGradleVersionsFromGradleUserHomeCaches implements UsedGradleVersions {

    private final VersionSpecificCacheDirectoryScanner directoryScanner;

    public UsedGradleVersionsFromGradleUserHomeCaches(GlobalScopedCacheBuilderFactory cacheBuilderFactory) {
        directoryScanner = new VersionSpecificCacheDirectoryScanner(cacheBuilderFactory.getRootDir());
    }

    @Override
    public SortedSet<GradleVersion> getUsedGradleVersions() {
        SortedSet<GradleVersion> result = Sets.newTreeSet();
        for (VersionSpecificCacheDirectory cacheDir : directoryScanner.getExistingDirectories()) {
            result.add(cacheDir.getVersion());
        }
        return result;
    }
}
