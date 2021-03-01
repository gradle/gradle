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

import org.gradle.api.internal.changedetection.state.SourceFileFilter;
import org.gradle.api.internal.file.DefaultFileTreeElement;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;

public class DefaultSourceFileFilter extends PatternSet implements SourceFileFilter {
    // TODO Perhaps instead of having a list of known file extensions, we should use a simple
    // heuristic to determine if the file is text or binary.  Something like what Git uses:
    // https://git.kernel.org/pub/scm/git/git.git/tree/xdiff-interface.c?h=v2.30.0#n187
    public static final String[] KNOWN_SOURCE_FILES = new String[] {
        "**/*.java",
        "**/*.groovy",
        "**/*.kotlin"
    };

    private final FileSystem fileSystem;

    public DefaultSourceFileFilter(FileSystem fileSystem) {
        super();
        this.fileSystem = fileSystem;
        include(KNOWN_SOURCE_FILES);
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        getIncludes().forEach(include -> hasher.putString("include:" + include));
        getExcludes().forEach(exclude -> hasher.putString("exclude:" + exclude));
    }

    @Override
    public boolean isSourceFile(File file) {
        return getAsSpec().isSatisfiedBy(DefaultFileTreeElement.of(file, fileSystem));
    }
}
