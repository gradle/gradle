/*
 * Copyright 2025 the original author or authors.
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Keep package-info files for packages split up into multiple directories/projects
 * from being different. If allowed to be different behavior can become nondeterministic
 * based on classpath ordering:
 * <ul>
 *     <li>Runtime annotations may kick in or not.</li>
 *     <li>Javadoc may pick up different files to generate package pages</li>
 * </ul>
 * Known violations are tracked in {@code src/changes/archunit-store}, see {@link PackageInfoBaseline}.
 */
public class PackageInfoTest {

    /**
     * Absolute path to settings directory, to relativize paths to package-info files.
     */
    private static final Path BASE_PATH = Paths.get(System.getProperty("org.gradle.architecture.package-info-base-path"));

    /**
     * One package, across every project that contributes to it. Mirrors {@code gradlebuild.packageinfo.model} in
     * build logic, which is not on this test's classpath.
     *
     * @param projects every project contributing sources to the package, whether it declares a package-info.java
     * @param packageInfo the package-info.java files declaring the package
     */
    private record PackageEntry(
        List<String> projects,
        List<PackageInfoFile> packageInfo
    ) {}

    /**
     * @param project the project the file belongs to
     * @param path the file's path, relative to {@link #BASE_PATH}
     */
    private record PackageInfoFile(
        String project,
        String path
    ) {}

    private Map<String, PackageEntry> getPackageInfo() {
        Path jsonPath = Paths.get(System.getProperty("org.gradle.architecture.package-info-json"));

        try (var reader = Files.newBufferedReader(jsonPath)) {
            return new Gson().fromJson(reader, new TypeToken<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to read package-info-json from " + jsonPath, e);
        }
    }

    @Test
    public void checkPackageInfoForInconsistencies() {
        SortedMap<String, String> violations = new TreeMap<>();
        for (Map.Entry<String, PackageEntry> entry : getPackageInfo().entrySet()) {
            List<Path> infoFiles = resolvePaths(entry.getValue());
            // there can't be conflicts if a package has a single package-info.java file assigned to it
            if (infoFiles.size() > 1 && !areIdentical(infoFiles)) {
                violations.put(entry.getKey(), describeInconsistency(entry.getKey(), infoFiles));
            }
        }
        PackageInfoBaseline.check("package-info-inconsistent", violations);
    }

    private List<Path> resolvePaths(PackageEntry entry) {
        return entry.packageInfo().stream()
            .map(file -> BASE_PATH.resolve(file.path()))
            .collect(Collectors.toList());
    }

    private boolean areIdentical(List<Path> infoFiles) {
        String referenceContent = readAsString(infoFiles.get(0));
        return infoFiles.subList(1, infoFiles.size()).stream()
            .allMatch(file -> referenceContent.equals(readAsString(file)));
    }

    /**
     * Lists the files grouped by content, largest group first, so a single odd one stands out.
     */
    private String describeInconsistency(String packageName, List<Path> infoFiles) {
        Map<String, List<String>> filesByContent = infoFiles.stream()
            .sorted() // Sort to maintain deterministic order of entries within a single group.
            .collect(Collectors.groupingBy(
                this::readAsString, // key - group by identical contents.
                Collectors.mapping(this::relativePackageInfoPath, Collectors.toList()) // value -> relativize paths,
                // then fold all paths with the same contents into one list.
            ));
        Comparator<List<String>> bySizeThenFirstElementDesc = Comparator.comparingInt((List<String> group) -> group.size())
            .reversed()
            .thenComparing(group -> group.get(0)); // Tie-breaker to make order total. There are no empty groups.
        List<List<String>> groups = filesByContent.values().stream().sorted(bySizeThenFirstElementDesc).toList();

        try (var message = new Formatter(Locale.ROOT)) {
            message.format("Inconsistent package-info files for package %s, %d distinct contents:", packageName, groups.size());
            for (List<String> group : groups) {
                message.format("\n\t\t %d %s:", group.size(), group.size() == 1 ? "file" : "files");
                for (var file : group) {
                    message.format("\n\t\t\t %s", file);
                }
            }
            return message.toString();
        }
    }

    private String relativePackageInfoPath(Path path) {
        return BASE_PATH.relativize(path).toString();
    }

    private String readAsString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed reading contents of " + path, e);
        }
    }
}
