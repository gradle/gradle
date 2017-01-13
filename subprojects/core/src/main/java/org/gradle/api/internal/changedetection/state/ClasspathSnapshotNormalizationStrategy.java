/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.cache.StringInterner;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy.getRelativeSnapshot;

public class ClasspathSnapshotNormalizationStrategy implements SnapshotNormalizationStrategy {
    public static final SnapshotNormalizationStrategy INSTANCE = new ClasspathSnapshotNormalizationStrategy();

    private ClasspathSnapshotNormalizationStrategy() {
    }

    @Override
    public boolean isPathAbsolute() {
        return false;
    }

    @Override
    public NormalizedFileSnapshot getNormalizedSnapshot(FileDetails fileDetails, StringInterner stringInterner) {
        // Ignore path of root files and directories
        if (fileDetails.isRoot()) {
            return new IgnoredPathFileSnapshot(fileDetails.getContent());
        }
        return getRelativeSnapshot(fileDetails, fileDetails.getContent(), stringInterner);
    }

    @Override
    public String toString() {
        return "CLASSPATH";
    }
}
