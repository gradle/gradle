/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.javadoc

/**
 * @deprecated No replacement
 */
@Deprecated
class AntJavadoc {
    void execute(List<File> sourceDirs, File destDir, Set<File> classpathFiles, String windowTitle, String maxMemory,
                 List<String> includes, List<String> excludes, boolean verbose, AntBuilder ant) {
        Map otherArgs = [:]
        if (maxMemory) {otherArgs.maxmemory = maxMemory}
        if (windowTitle) {
            otherArgs.windowtitle = windowTitle
            otherArgs.doctitle = "<p>$windowTitle</p>"
        }
        ant.javadoc([destdir: destDir, failonerror: true, verbose: verbose] + otherArgs) {
            sourceDirs.each {
                fileset(dir: it) {
                    includes.each {
                        include(name: it)
                    }
                    excludes.each {
                        exclude(name: it)
                    }
                    // This looks wrong. However, javadoc fails when package.html files are included explicitly. Javadoc
                    // will include them in the documentation even if they are not included. So, exclude them.
                    exclude(name: '**/package.html')
                }
            }
            classpathFiles.each {
                classpath(location: it)
            }
        }
    }
}
