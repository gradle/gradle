/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests;

import org.gradle.util.GradleUtil;

import java.io.File;

public class GradleBackedArtifactBuilder implements ArtifactBuilder {
    private final GradleExecuter executer;
    private final TestFile rootDir;

    public GradleBackedArtifactBuilder(GradleExecuter executer, File rootDir) {
        this.executer = executer;
        this.rootDir = new TestFile(rootDir);
    }

    public TestFile sourceFile(String path) {
        return rootDir.file("src/main/java", path);
    }

    public void buildJar(File jarFile) {
        rootDir.file("build.gradle").writelns(
                "usePlugin 'java'",
                "test.enabled = false",
                String.format("jar.destinationDir = file('%s')", GradleUtil.unbackslash(jarFile.getParentFile()),
                String.format("jar.customName = '%s'", jarFile.getName())
        );
        executer.inDirectory(rootDir.asFile()).withTasks("clean", "jar").run();
    }
}
