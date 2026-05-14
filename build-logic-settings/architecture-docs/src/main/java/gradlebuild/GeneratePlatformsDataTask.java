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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class GeneratePlatformsDataTask extends DefaultTask {

    public static final class PlatformData {

        private final String name;
        private final List<String> dirs;
        private final List<String> uses;

        public PlatformData(String name, List<String> dirs, List<String> uses) {
            this.name = name;
            this.dirs = dirs;
            this.uses = uses;
        }
    }

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract ListProperty<Platform> getPlatforms();

    @TaskAction
    public void action() {
        List<Platform> allPlatforms = getPlatforms().get();
        List<PlatformData> data = new ArrayList<>();
        for (Platform platform : allPlatforms) {
            List<String> dirs = platform.getChildren().isEmpty()
                ? Collections.singletonList(platform.getName())
                : platform.getChildren().stream().map(ArchitectureModule::getName).collect(Collectors.toList());
            List<String> uses = platform.getUses().stream()
                .map(use -> single(allPlatforms, use).getName())
                .collect(Collectors.toList());
            data.add(new PlatformData(platform.getName(), dirs, uses));
        }
        try {
            Files.write(getOutputFile().get().getAsFile().toPath(), new Gson().toJson(data).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Platform single(List<Platform> platforms, ElementId use) {
        Platform match = null;
        for (Platform platform : platforms) {
            if (platform.getId() == use) {
                if (match != null) {
                    throw new IllegalArgumentException("Collection contains more than one matching element.");
                }
                match = platform;
            }
        }
        if (match == null) {
            throw new IllegalArgumentException("Collection contains no element matching the predicate.");
        }
        return match;
    }
}
