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
package org.gradle.modules

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec

/**
 * Patch JARs by removing/adding specific entries from/into the specified jar file.
 *
 * This is used to patch the kotlin-compiler-embeddable jar, which has:
 * <ul>
 *     <li>a bogus `CharsetProvider` entry,</li>
 *     <li>an old version of `native-platform`,</li>
 *     <li>an old version of `jansi`.</li>
 * </ul>
 */
@CompileStatic
class JarPatcher {

    Project project

    Configuration runtime

    String jarFile

    List<String> excludedEntries = []
    Map<String, List<String>> includedJars = [:]

    File temporaryDir

    JarPatcher(Project project, File temporaryDir, Configuration runtime, String jarFile) {
        this.project = project
        this.runtime = runtime
        this.jarFile = jarFile
        this.temporaryDir = temporaryDir
    }

    JarPatcher exclude(String exclude) {
        excludedEntries << exclude
        this
    }

    JarPatcher includeJar(String includedJar, String... includes) {
        includedJars.put(includedJar, includes as List<String> ?: [] as List<String>)
        this
    }

    def writePatchedFilesTo(File outputDir) {
        def originalFile = runtime.files.find { it.name.startsWith(jarFile) }
        def unpackDir = unpack(originalFile)

        def patchedFile = new File(outputDir, originalFile.name)
        pack(unpackDir, patchedFile)
    }

    private File unpack(File file) {
        def unpackDir = new File(temporaryDir, "excluding-" + file.name)
        project.sync(new Action<CopySpec>() {
            @Override
            void execute(CopySpec spec) {
                spec.into(unpackDir)
                spec.from(project.zipTree(file))
                spec.exclude(excludedEntries)
            }
        })
        unpackDir
    }

    @CompileDynamic
    private void pack(File baseDir, File destFile) {
        Map<File, List<String>> resolvedIncludes = [:]
        includedJars.each { jarPrefix, includes ->
            runtime.files.findAll { it.name.startsWith(jarPrefix) }.each { includedJar ->
                resolvedIncludes.put(includedJar, includes)
            }
        }
        project.copy(new Action<CopySpec>() {
            @Override
            void execute(CopySpec spec) {
                spec.into(baseDir)
                resolvedIncludes.each { sourceJar, includes ->
                    spec.from(project.zipTree(sourceJar), new Action<CopySpec>() {
                        @Override
                        void execute(CopySpec jarSpec) {
                            includes.each { include ->
                                jarSpec.include(include)
                            }
                        }
                    })

                }
            }
        })
        project.ant.zip(basedir: baseDir, destfile: destFile)
    }
}
