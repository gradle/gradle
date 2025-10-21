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
import org.junit.jupiter.api.function.Executable;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Keep package-info files for packages split up into multiple directories/projects
 * from being different. If allowed to be different behaviour can become nondeterministic
 * based on classpath ordering:
 * <ul>
 *     <li>Runtime annotations may kick in or not.</li>
 *     <li>Javadoc may pick up different files to generate package pages</li>
 * </ul>
 */
public class PackageInfoTest {

    private static final Path BASE_PATH = Paths.get(System.getProperty("org.gradle.architecture.package-info-base-path"));

    private static Map<String, List<Path>> getPackageInfo() {
        Path jsonPath = Paths.get(System.getProperty("org.gradle.architecture.package-info-json"));

        Map<String, List<String>> rawPackageInfo;
        try (Reader reader = new FileReader(jsonPath.toFile())) {
            rawPackageInfo = new Gson().fromJson(reader, new TypeToken<Map<String, List<String>>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return rawPackageInfo.entrySet()
            .stream()
            .collect(Collectors.toMap(
               Map.Entry::getKey,
               entry -> entry.getValue().stream()
                   .map(BASE_PATH::resolve)
                   .collect(Collectors.toList())
            ));
    }

    @Test
    public void checkPackageInfoForInconsistencies() {
        Map<String, List<Path>> allPackageInfo = getPackageInfo();

        assertAll("Checking public API package info",
            allPackageInfo.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1) // there can't be conflicts if a package has a single package-info.java file assigned to it
                .filter(entry -> ArchUnitFixture.InGradlePublicApiPackages.test(entry.getKey())) // we don't care about packages that don't contain public API code
                .map(entry -> (Executable) () -> assertPackageInfoFilesAreIdentical(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList())
        );
    }

    private static void assertPackageInfoFilesAreIdentical(String packageName, List<Path> infoFiles) {
        String referenceContent = readAsString(infoFiles.get(0));
        boolean hasInconsistencies = infoFiles.subList(1, infoFiles.size()).stream()
            .anyMatch(file -> !referenceContent.equals(readAsString(file)));
        if (hasInconsistencies) {
            fail("Inconsistent package-info files for package " + packageName + ": " +
                infoFiles.stream()
                    .map(file -> BASE_PATH.relativize(file).toString())
                    .collect(Collectors.joining("\n\t\t", "\n\t\t", ""))
            );
        }
    }

    private static String readAsString(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed reading contents of " + path, e);
        }
    }
}
