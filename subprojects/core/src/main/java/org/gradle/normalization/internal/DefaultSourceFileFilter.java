/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.normalization.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.internal.changedetection.state.SourceFileFilter;
import org.gradle.internal.hash.Hasher;

import javax.annotation.Nullable;
import java.util.Set;

import static org.apache.commons.io.FilenameUtils.getExtension;

public class DefaultSourceFileFilter implements SourceFileFilter {
    // TODO Perhaps instead of having a list of known file extensions, we should use a simple
    // heuristic to determine if the file is text or binary.  Something like what Git uses:
    // https://git.kernel.org/pub/scm/git/git.git/tree/xdiff-interface.c?h=v2.30.0#n187
    public static final Set<String> KNOWN_SOURCE_FILE_EXTS = ImmutableSet.of(
        "java",
        "groovy",
        "kt"
    );
    private final Set<String> sourceFileExtensions;

    public DefaultSourceFileFilter() {
        super();
        this.sourceFileExtensions = Sets.newHashSet(KNOWN_SOURCE_FILE_EXTS);
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        sourceFileExtensions.forEach(hasher::putString);
    }

    @Override
    public boolean isSourceFile(@Nullable String path) {
        return sourceFileExtensions.contains(getExtension(path));
    }
}
