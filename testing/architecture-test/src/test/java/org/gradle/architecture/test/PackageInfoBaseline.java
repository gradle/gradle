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

package org.gradle.architecture.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;

/**
 * A violation store for rules over package-info data.
 * <p>
 * Those rules are plain JUnit tests over source files, not ArchUnit rules over bytecode, so they cannot use
 * {@code FreezingArchRule}. This is the equivalent: known violations live in a text file next to the ArchUnit stores,
 * and the rule only fails when the set of violations changes.
 * <p>
 * Like ArchUnit's freeze, this is a ratchet in both directions: a violation that is <em>no longer</em> present also
 * fails the rule, so the store can only shrink, and it shrinks in the change that fixes the violation. Refreezing with
 * {@code -ParchunitRefreeze} rewrites the store to match reality.
 * <p>
 * The store is keyed by the violation's key (a package name), which is stable across unrelated edits.
 */
final class PackageInfoBaseline {

    private static final Path STORE_DIR = Paths.get(System.getProperty("archunit.freeze.store.default.path"));
    private static final boolean REFREEZE = Boolean.getBoolean("archunit.freeze.refreeze");

    private PackageInfoBaseline() {
    }

    /**
     * Checks the current violations of a rule against its stored baseline.
     *
     * @param ruleName the name of the rule, also the base name of the store file
     * @param violations the current violations, keyed by a stable identifier (a package name), mapped to a human-readable description
     */
    static void check(String ruleName, SortedMap<String, String> violations) {
        Path storeFile = STORE_DIR.resolve(ruleName + ".txt");

        if (REFREEZE) {
            writeStore(storeFile, violations.keySet());
            return;
        }

        Set<String> baseline = readStore(storeFile);
        Set<String> newViolations = new TreeSet<>(violations.keySet());
        newViolations.removeAll(baseline);
        Set<String> staleEntries = new TreeSet<>(baseline);
        staleEntries.removeAll(violations.keySet());

        if (newViolations.isEmpty() && staleEntries.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("Violations of rule '").append(ruleName).append("' changed.");
        if (!newViolations.isEmpty()) {
            message.append("\nNew violations:");
            for (String key : newViolations) {
                message.append("\n\t").append(violations.get(key));
            }
        }
        if (!staleEntries.isEmpty()) {
            message.append("\nStored violations that no longer occur, remove them from ").append(storeFile.getFileName()).append(":");
            for (String key : staleEntries) {
                message.append("\n\t").append(key);
            }
        }
        message.append("\nPlease refreeze and check the differences by running ./gradlew architecture-test:test -ParchunitRefreeze");
        fail(message.toString());
    }

    private static Set<String> readStore(Path storeFile) {
        if (!Files.exists(storeFile)) {
            return Set.of();
        }
        try {
            return Files.readAllLines(storeFile, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new RuntimeException("Failed reading violation store " + storeFile, e);
        }
    }

    private static void writeStore(Path storeFile, Set<String> keys) {
        // Like ArchUnit's store: an empty file for a rule without violations, LF line endings on every platform.
        String content = new TreeSet<>(keys).stream()
            .map(key -> key + "\n")
            .collect(Collectors.joining());
        try {
            Files.createDirectories(storeFile.getParent());
            Files.writeString(storeFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed writing violation store " + storeFile, e);
        }
    }
}
