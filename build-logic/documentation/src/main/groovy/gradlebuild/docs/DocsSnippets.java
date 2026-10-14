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

package gradlebuild.docs;

import org.gradle.util.internal.GUtil;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds the documentation snippets under {@code src/snippets} and names them the way
 * the {@code org.gradle.samples} plugin did, so that test IDs and exclusion lists stay stable.
 */
public final class DocsSnippets {
    public static final List<String> DSLS = Collections.unmodifiableList(Arrays.asList("groovy", "kotlin"));

    /**
     * Top-level directories of {@code src/snippets} that hold something other than tested snippets: build logic
     * included by integration tests, and snippets kept only for reference.
     */
    private static final Set<String> UNTESTED_TOP_LEVEL_DIRS = new HashSet<>(Arrays.asList("integration-tests", "unused"));

    private DocsSnippets() {
    }

    public static final class Snippet {
        private final File directory;
        private final String installName;
        private final List<String> dsls;
        private final boolean explicitSanityCheck;

        Snippet(File directory, String installName, List<String> dsls, boolean explicitSanityCheck) {
            this.directory = directory;
            this.installName = installName;
            this.dsls = dsls;
            this.explicitSanityCheck = explicitSanityCheck;
        }

        public File getDirectory() {
            return directory;
        }

        /**
         * Directory name under the testing root, for example {@code snippet-best-practices-use-gav-string-do}.
         */
        public String getInstallName() {
            return installName;
        }

        public List<String> getDsls() {
            return dsls;
        }

        /**
         * Whether {@code tests/} or {@code tests-common/} already contain a sanity check, in which case none is generated.
         */
        public boolean hasExplicitSanityCheck() {
            return explicitSanityCheck;
        }
    }

    /**
     * A snippet is a directory containing a {@code groovy/} or {@code kotlin/} directory.
     */
    public static List<Snippet> discover(File snippetsRoot) {
        List<File> directories = new ArrayList<>();
        findSnippetDirectories(snippetsRoot, true, directories);
        List<Snippet> snippets = new ArrayList<>(directories.size());
        for (File directory : directories) {
            String relativePath = snippetsRoot.toPath().relativize(directory.toPath()).toString();
            String installName = GUtil.toWords("snippet-" + relativePath.replace(File.separatorChar, '-'), '-');
            List<String> dsls = DSLS.stream().filter(dsl -> new File(directory, dsl).isDirectory()).collect(Collectors.toList());
            boolean explicitSanityCheck = containsSanityCheck(new File(directory, "tests")) || containsSanityCheck(new File(directory, "tests-common"));
            snippets.add(new Snippet(directory, installName, dsls, explicitSanityCheck));
        }
        return snippets;
    }

    private static void findSnippetDirectories(File directory, boolean topLevel, List<File> result) {
        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        Arrays.sort(children);
        for (File child : children) {
            if (topLevel && UNTESTED_TOP_LEVEL_DIRS.contains(child.getName())) {
                continue;
            }
            if (new File(child, "kotlin").exists() || new File(child, "groovy").exists()) {
                result.add(child);
            } else {
                findSnippetDirectories(child, false, result);
            }
        }
    }

    private static boolean containsSanityCheck(File directory) {
        if (!directory.isDirectory()) {
            return false;
        }
        try (Stream<Path> files = Files.walk(directory.toPath())) {
            return files.map(path -> path.getFileName().toString())
                .anyMatch(name -> name.endsWith(".sample.conf") && name.toLowerCase(Locale.ROOT).contains("sanitycheck"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
