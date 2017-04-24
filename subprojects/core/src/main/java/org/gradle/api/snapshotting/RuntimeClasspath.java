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

package org.gradle.api.snapshotting;

import org.gradle.api.Incubating;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Snapshotter for files in a classpath entry.
 *
 * @since 4.0
 */
@Incubating
public class RuntimeClasspath implements SnapshotterConfiguration {
    private final List<String> excludes = new LinkedList<String>();

    public void exclude(String... patterns) {
        excludes.addAll(Arrays.asList(patterns));
    }

    public boolean isSatisfiedBy(String element) {
        for (String exclude : excludes) {
            if (element.endsWith(exclude)) {
                return false;
            }
        }
        return true;
    }

    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putInt(excludes.size());
        for (String exclude : excludes) {
            hasher.putString(exclude);
        }
    }
}
