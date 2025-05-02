/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.binarycompatibility;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

/**
 * Extracts Gradle API information from the given Gradle distribution archives.
 */
@CacheableTask
public abstract class ExtractGradleApiInfoTask extends DefaultTask {

    private static final String GRADLE_API_INFO_JAR = "gradle-runtime-api-info";
    private static final String UPGRADED_PROPERTIES_FILE = "upgraded-properties.json";

    public ExtractGradleApiInfoTask() {
        getGradleApiInfoJarPrefix().convention(GRADLE_API_INFO_JAR);
    }

    @Input
    public abstract Property<String> getGradleApiInfoJarPrefix();

    @CompileClasspath
    public abstract ConfigurableFileCollection getCurrentDistributionJars();

    @CompileClasspath
    public abstract ConfigurableFileCollection getBaselineDistributionJars();

    @OutputFile
    public abstract RegularFileProperty getCurrentUpgradedProperties();

    @OutputFile
    public abstract RegularFileProperty getBaselineUpgradedProperties();

    @TaskAction
    public void execute() {
        extractUpgradedProperties(getCurrentDistributionJars(), getCurrentUpgradedProperties());
        extractUpgradedProperties(getBaselineDistributionJars(), getBaselineUpgradedProperties());
    }

    private void extractUpgradedProperties(FileCollection from, RegularFileProperty to) {
        String gradleApiInfoJarPrefix = getGradleApiInfoJarPrefix().get();
        File gradleRuntimeApiInfoJar = from.filter(file -> file.getName().startsWith(gradleApiInfoJarPrefix)).getSingleFile();
        URI uri = URI.create("jar:" + gradleRuntimeApiInfoJar.getAbsoluteFile().toURI());
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Path upgradedPropertiesJson = fs.getPath(UPGRADED_PROPERTIES_FILE);
            if (Files.exists(upgradedPropertiesJson)) {
                Files.copy(upgradedPropertiesJson, to.getAsFile().get().toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                to.getAsFile().get().delete();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
