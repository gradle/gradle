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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.file.FileType;

import javax.annotation.Nullable;

/**
 * Path normalization strategy for output files.
 *
 * We use the absolute path of the files and ignore missing files and empty root directories.
 */
public class OutputPathNormalizationStrategy implements PathNormalizationStrategy {

    private static final OutputPathNormalizationStrategy INSTANCE = new OutputPathNormalizationStrategy();

    public static OutputPathNormalizationStrategy getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isPathAbsolute() {
        return true;
    }

    @Nullable
    @Override
    public NormalizedFileSnapshot getNormalizedSnapshot(FileSnapshot fileSnapshot, StringInterner stringInterner) {
        if (fileSnapshot.getType() == FileType.Missing) {
            return null;
        }
        return new NonNormalizedFileSnapshot(fileSnapshot.getPath(), fileSnapshot.getContent());
    }
}
