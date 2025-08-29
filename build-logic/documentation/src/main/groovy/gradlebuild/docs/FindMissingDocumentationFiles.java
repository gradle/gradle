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

package gradlebuild.docs;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Arrays;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

/**
 * A Java class to verify that all .adoc files and their anchors listed in a JSON file exist
 * within a specified directory.
 */
@CacheableTask
public abstract class FindMissingDocumentationFiles extends DefaultTask {
    private static final Set<String> EXCLUDED_FILES = new HashSet<>(Arrays.asList(
        "temp.adoc",
        "userguide_single.adoc"
    ));

    private static final Set<String> EXCLUDED_ANCHORS = new HashSet<>(Arrays.asList(
        // properties have been deprecated in Gradle 9.0.0
        "sec:war_convention_properties",
        "sec:ear_convention_properties",
        "sec:base_plugin_conventions",
        "sec:project_reports_convention_properties",
        "sec:kotlin_dsl_about_conventions",
        "sec:java_convention_properties",
        // config cache items that have now been implemented
        "config_cache:not_yet_implemented:secrets",
        "config_cache:not_implemented:store_parallel_execution",
        "config_cache:not_yet_implemented:storing_lambdas",
        // reroute to cookbook, no anchors needed
        "build_jenkins",
        "build_teamcity",
        "build_github_actions",
        "sec:configure_github_actions",
        "build_travis",
        // fixed with redirection (javascript in deleted pages)
        "sec:component_selection_rules",
        "sub:declaring_dependency_with_dynamic_version",
        "sub:declaring_dependency_with_changing_version",
        "sec:controlling_dependency_caching_programmatically",
        "sec:offline-mode",
        "sec:controlling_dependency_caching_command_line",
        "sec:dynamic_versions_and_changing_modules",
        "single-version-declarations",
        "version_ordering",
        "sec:declaring_without_version",
        "rich-version-constraints",
        "dependency_management_in_gradle",
        "sec:dependency-mgmt-in-gradle"
    ));

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDocumentationRoot();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getJsonFilesDirectory();

    @TaskAction
    public void checkMissingFiles() {
        String directoryPath = getDocumentationRoot().get().toString();
        FileCollection jsonFiles = getJsonFilesDirectory().getAsFileTree().filter(file -> file.getName().endsWith(".json"));

        try {
            List<String> allErrors = new ArrayList<>();
            Set<String> allExistingAnchors = findAllAdocAnchors(directoryPath);

            for (File jsonFile : jsonFiles) {
                getLogger().info("Verifying documentation for file: {}", jsonFile.getName());
                allErrors.addAll(findMissingAdocFiles(jsonFile, directoryPath));
                allErrors.addAll(findMissingAnchors(jsonFile, allExistingAnchors));
            }

            if (!allErrors.isEmpty()) {
                System.out.println("Found documentation files or anchors that do not exist:");
                for (String error : allErrors) {
                    System.out.println(error);
                }
                throw new RuntimeException("Found " + allErrors.size() + " missing documentation files or anchors.");
            } else {
                System.out.println("All documentation files and anchors from the JSON files were found in the directory.");
            }
        } catch (IOException e) {
            System.err.println("An error occurred during verification: " + e.getMessage());
            throw new RuntimeException("Error during file verification", e);
        }
    }

    private static class AdocFileEntry {
        @SerializedName("filename")
        String filename;
        @SerializedName("anchors")
        List<String> anchors;
    }

    /**
     * Reads a JSON file and finds any .adoc files listed that are missing from the given directory.
     */
    private static List<String> findMissingAdocFiles(File jsonFile, String directoryPath) throws IOException {
        List<String> missingFiles = new ArrayList<>();

        Gson gson = new Gson();
        List<AdocFileEntry> entries;
        try (FileReader reader = new FileReader(jsonFile)) {
            entries = gson.fromJson(reader, new TypeToken<List<AdocFileEntry>>() {}.getType());
        }

        Set<String> foundAdocFiles = new HashSet<>();
        try (Stream<Path> pathStream = Files.walk(Paths.get(directoryPath))) {
            pathStream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".adoc"))
                .map(p -> p.getFileName().toString())
                .forEach(foundAdocFiles::add);
        }

        for (AdocFileEntry entry : entries) {
            String filename = entry.filename;
            if (!EXCLUDED_FILES.contains(filename) && !foundAdocFiles.contains(filename)) {
                missingFiles.add("- The file '" + filename + "' from " + jsonFile.getName() + " was not found in the directory.");
            }
        }

        return missingFiles;
    }

    /**
     * Finds and collects all anchors from all .adoc files in a directory.
     */
    private static Set<String> findAllAdocAnchors(String directoryPath) throws IOException {
        Set<String> allAnchors = new HashSet<>();
        try (Stream<Path> pathStream = Files.walk(Paths.get(directoryPath))) {
            pathStream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".adoc"))
                .forEach(file -> {
                    try {
                        // The regex `^\\s*\\[\\[(.*)\\]\\]` looks for lines starting with '[[...]]'
                        // and captures the content within the brackets.
                        Files.readAllLines(file).forEach(line -> {
                            if (line.matches("^\\s*\\[\\[(.*)\\]\\].*")) {
                                String anchorName = line.replaceAll("^\\s*\\[\\[(.*)\\]\\].*$", "$1");
                                allAnchors.add(anchorName);
                            }
                        });
                    } catch (IOException e) {
                        throw new RuntimeException("Error reading file: " + file, e);
                    }
                });
        }
        return allAnchors;
    }

    /**
     * Checks if all anchors from a JSON file exist in a collective set of all anchors.
     */
    private static List<String> findMissingAnchors(File jsonFile, Set<String> allExistingAnchors) throws IOException {
        List<String> missingAnchors = new ArrayList<>();
        Gson gson = new Gson();
        List<AdocFileEntry> entries;
        try (FileReader reader = new FileReader(jsonFile)) {
            entries = gson.fromJson(reader, new TypeToken<List<AdocFileEntry>>() {}.getType());
        }

        for (AdocFileEntry entry : entries) {
            for (String anchor : entry.anchors) {
                if (!EXCLUDED_ANCHORS.contains(anchor) && !allExistingAnchors.contains(anchor)) {
                    missingAnchors.add("- The anchor '" + anchor + "' from file '" + entry.filename + "' (" + jsonFile.getName() + ") was not found in any documentation file.");
                }
            }
        }
        return missingAnchors;
    }
}
