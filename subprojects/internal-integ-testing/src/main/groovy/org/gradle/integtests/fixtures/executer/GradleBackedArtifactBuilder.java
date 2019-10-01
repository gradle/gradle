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

import org.apache.commons.io.FilenameUtils;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;

import java.io.File;

public class GradleBackedArtifactBuilder implements ArtifactBuilder {
    private final GradleExecuter executer;
    private final TestFile rootDir;

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
    public void buildJar(File jarFile) {
        buildJar(jarFile, false);
    }

    public void buildJar(File jarFile, boolean deleteIfExists) {
        if (deleteIfExists && jarFile.exists()) {
            if(!jarFile.delete()) {
                throw new IllegalStateException("Couldn't delete file " + jarFile);
            }
        }
        String conf = executer.getDistribution().getVersion().compareTo(GradleVersion.version("3.4")) < 0 ? "compile" : "implementation";
        String destinationDir = executer.getDistribution().getVersion().compareTo(GradleVersion.version("5.1")) < 0 ? "destinationDir" : "destinationDirectory";
        String archiveName = executer.getDistribution().getVersion().compareTo(GradleVersion.version("5.0")) < 0 ? "archiveName" : "archiveFileName";
        rootDir.file("settings.gradle").touch();
        rootDir.file("build.gradle").writelns(
                "apply plugin: 'java'",
                String.format("dependencies { %s gradleApi() }", conf),
                String.format("jar.%s = file('%s')", destinationDir, FilenameUtils.separatorsToUnix(jarFile.getParent())),
                String.format("jar.%s = '%s'", archiveName, jarFile.getName()),
                // disable jar file caching to prevent file locking
                "new URL(\"jar:file://valid_jar_url_syntax.jar!/\").openConnection().setDefaultUseCaches(false)"
        );
        executer.inDirectory(rootDir).withTasks("clean", "jar").run();
    }
}
