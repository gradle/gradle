
/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Removes specific entries from the specified jar file.
 * This is used to patch the kotlin-compiler-embeddable jar, which has a bogus `CharsetProvider` entry.
 */
class ExcludeEntryPatcher {

    Project project

    Configuration runtime

    String jarFile

    List<String> excludes = []

    ExcludeEntryPatcher(Project project, Configuration runtime, String jarFile) {
        this.project = project
        this.runtime = runtime
        this.jarFile = jarFile
    }

    def exclude(String exclude) {
        excludes << exclude
        this
    }

    def writePatchedFilesTo(File outputDir) {
        def originalFile = runtime.files.find { it.name.startsWith(jarFile) }
        def unpackDir = unpack(originalFile)

        def patchedFile = new File(outputDir, originalFile.name)
        pack(unpackDir, patchedFile)
    }

    private File unpack(File file) {
        def unpackDir = project.file("${project.buildDir}/external/unpack/${file.name}")
        project.copy { spec ->
            spec.into(unpackDir)
            spec.from(project.zipTree(file))
            spec.exclude this.excludes
        }
        unpackDir
    }

    private void pack(File baseDir, File destFile) {
        project.ant.zip(basedir: baseDir, destfile: destFile)
    }
}
