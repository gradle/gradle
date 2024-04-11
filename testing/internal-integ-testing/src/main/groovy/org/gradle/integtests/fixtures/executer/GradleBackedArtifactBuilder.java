/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import org.gradle.internal.UncheckedException;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class GradleBackedArtifactBuilder implements ArtifactBuilder {
    private final GradleExecuter executer;
    private final TestFile rootDir;
    private final Map<String, String> manifestAttributes = new LinkedHashMap<>();

    private Boolean shouldPreserveTimestamps;

    public GradleBackedArtifactBuilder(GradleExecuter executer, File rootDir) {
        this.executer = executer;
        this.rootDir = new TestFile(rootDir);
    }

    @Override
    public TestFile sourceFile(String path) {
        return rootDir.file("src/main/java", path);
    }

    @Override
    public TestFile resourceFile(String path) {
        return rootDir.file("src/main/resources", path);
    }

    @Override
    public void manifestAttributes(Map<String, String> attributes) {
        manifestAttributes.putAll(attributes);
    }

    @Override
    public void preserveTimestamps(boolean preserveTimestamps) {
        if (isGradleExecuterVersionLessThan("3.4")) {
            throw new IllegalArgumentException("Cannot set up timestamps on Gradle before 3.4");
        }
        shouldPreserveTimestamps = preserveTimestamps;
    }

    @Override
    public void buildJar(File jarFile) {
        String conf = isGradleExecuterVersionLessThan("3.4") ? "compile" : "implementation";
        String destinationDir = isGradleExecuterVersionLessThan("5.1") ? "destinationDir" : "destinationDirectory";
        String archiveName = isGradleExecuterVersionLessThan("5.0") ? "archiveName" : "archiveFileName";

        rootDir.mkdirs();
        rootDir.file("settings.gradle").touch();
        try {
            try (PrintWriter writer = new PrintWriter(rootDir.file("build.gradle"))) {
                writer.println("apply plugin: 'java'");
                writer.println(String.format("dependencies { %s gradleApi() }", conf));
                writer.println("jar {");
                if (shouldPreserveTimestamps != null) {
                    writer.println(String.format("preserveFileTimestamps = %s", shouldPreserveTimestamps));
                }
                writer.println(String.format("%s = file('%s')", destinationDir, jarFile.getParentFile().toURI()));
                writer.println(String.format("%s = '%s'", archiveName, jarFile.getName()));
                if (!manifestAttributes.isEmpty()) {
                    writer.println("def attrs = [:]");
                    for (Map.Entry<String, String> entry : manifestAttributes.entrySet()) {
                        writer.println(String.format("attrs.put(\"%s\", \"%s\")", entry.getKey(), entry.getValue()));
                    }
                    writer.println("manifest.attributes(attrs)");
                }
                writer.println("}");
            }
        } catch (FileNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        executer.inDirectory(rootDir).withTasks("clean", "jar").run();
    }

    private boolean isGradleExecuterVersionLessThan(String version) {
        return executer.getDistribution().getVersion().compareTo(GradleVersion.version(version)) < 0;
    }
}
