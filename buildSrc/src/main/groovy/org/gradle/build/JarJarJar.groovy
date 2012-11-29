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

package org.gradle.build

import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import com.tonicsystems.jarjar.Main as JarJarMain

/*
* a Jar task that performs JarJar repackaging after archive is created
* */
class JarJarJar extends Jar {
    @Input def rules = [:]
    @Input def keeps = []

    public JarJarJar() {
        doLast {
            executeJarJar();
        }
        doLast {
            fixEmptyFoldersWithJarJar()
        }
    }

    void executeJarJar() {
        File tempRuleFile = new File(getTemporaryDir(), "jarjar.rules.txt")
        writeRuleFile(tempRuleFile)
        JarJarMain.main("process", tempRuleFile.absolutePath, getArchivePath().absolutePath, getArchivePath().absolutePath)
    }

    void fixEmptyFoldersWithJarJar() {
        def withNoEmptyDirs = "${getTemporaryDir()}/withNoEmptyDirs"
        project.copy{
            from project.zipTree(getArchivePath().absolutePath)
            into withNoEmptyDirs
            includeEmptyDirs = false
        }
        project.ant {
            zip(destfile: getArchivePath(), update: false) {
                fileset(dir: withNoEmptyDirs)
            }
        }
    }

    void rule(String pattern, String result) {
        rules[pattern] = result
    }

    void keep(String pattern) {
        keeps << pattern
    }

    private void writeRuleFile(File ruleFile) {
        ruleFile.withPrintWriter { writer ->
            rules.each {pattern, result ->
                writer.println("rule ${pattern} ${result}")
            }
            keeps.each {pattern ->
                writer.println("keep ${pattern}")
            }
        }
    }
}
