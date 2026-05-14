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

package gradlebuild;

import com.google.gson.Gson;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CacheableTask
public abstract class GeneratePackageInfoDataTask extends DefaultTask {

    private static final Pattern PACKAGE_LINE_REGEX = Pattern.compile("package\\s*([^;\\s]+)\\s*;");

    public static FileCollection findPackageInfoFiles(ObjectFactory objects, Provider<List<File>> projectBaseDirs) {
        return objects.fileCollection().from(projectBaseDirs.map(dirs -> {
            List<File> sourceRoots = new ArrayList<>();
            for (File projectDir : dirs) {
                sourceRoots.add(new File(projectDir, "src/main/java"));
                sourceRoots.add(new File(projectDir, "src/main/groovy"));
            }
            return sourceRoots;
        })).getAsFileTree().matching(pattern -> pattern.include("**/package-info.java")).filter(File::isFile);
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getPackageInfoFiles();

    /**
     * Preserve the project path in the cache key, since packageInfoFiles are fingerprinted relative to each source root.
     */
    @Input
    public List<String> getPackageInfoFilePaths() {
        return getPackageInfoFiles().getFiles().stream()
            .map(file -> baseDir.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/'))
            .sorted()
            .collect(Collectors.toList());
    }

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    private final File baseDir = getProject().getLayout().getSettingsDirectory().getAsFile();

    @TaskAction
    public void action() {
        Map<String, List<String>> outputData = new LinkedHashMap<>();
        for (File packageInfoFile : getPackageInfoFiles().getFiles()) {
            String packageLine = firstPackageLine(packageInfoFile);
            Matcher matcher = PACKAGE_LINE_REGEX.matcher(packageLine);
            if (!matcher.find()) {
                throw new IllegalStateException("Could not find a package declaration in " + packageInfoFile);
            }
            String packageName = matcher.group(1);
            String relativePath = baseDir.toPath().relativize(packageInfoFile.toPath()).toString();
            outputData.computeIfAbsent(packageName, key -> new ArrayList<>()).add(relativePath);
        }
        try {
            Files.write(getOutputFile().get().getAsFile().toPath(), new Gson().toJson(outputData).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String firstPackageLine(File packageInfoFile) {
        try (Stream<String> lines = Files.lines(packageInfoFile.toPath())) {
            return lines.filter(line -> line.startsWith("package")).findFirst()
                .orElseThrow(() -> new IllegalStateException("No package declaration found in " + packageInfoFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
