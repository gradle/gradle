/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.file.excludes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jspecify.annotations.NullMarked;

/**
 * The built-in set of patterns Gradle excludes by default from file collection
 * scanning (copy, archive, file collections, etc.).
 *
 * <p>Historically these were sourced from {@code org.apache.tools.ant.DirectoryScanner.DEFAULTEXCLUDES}.
 * Gradle now owns this list directly so it can be evolved independently of Ant and so the
 * Ant dependency can eventually be removed from Gradle's runtime classpath.</p>
 */
@NullMarked
public final class GradleDefaultExcludes {

    private GradleDefaultExcludes() {
    }

    /**
     * The default exclude patterns. Ported verbatim from Ant 1.10.17's
     * {@code DirectoryScanner.DEFAULTEXCLUDES} to preserve behavioral parity.
     */
    public static final ImmutableList<String> DEFAULT_EXCLUDES = ImmutableList.<String>builder().add(
        // Miscellaneous typical temporary files
        "**/*~",
        "**/#*#",
        "**/.#*",
        "**/%*%",
        "**/._*",

        // CVS
        "**/CVS",
        "**/CVS/**",
        "**/.cvsignore",

        // SCCS
        "**/SCCS",
        "**/SCCS/**",

        // Visual SourceSafe
        "**/vssver.scc",

        // Subversion
        "**/.svn",
        "**/.svn/**",

        // Git
        "**/.git",
        "**/.git/**",
        "**/.gitattributes",
        "**/.gitignore",
        "**/.gitmodules",

        // Mercurial
        "**/.hg",
        "**/.hg/**",
        "**/.hgignore",
        "**/.hgsub",
        "**/.hgsubstate",
        "**/.hgtags",

        // Bazaar
        "**/.bzr",
        "**/.bzr/**",
        "**/.bzrignore",

        // Mac OSX
        "**/.DS_Store"
    ).build();

    public static final ImmutableSet<String> DEFAULT_EXCLUDES_SET = ImmutableSet.copyOf(DEFAULT_EXCLUDES);
}
