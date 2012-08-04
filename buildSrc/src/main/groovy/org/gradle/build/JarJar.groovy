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

import com.tonicsystems.jarjar.Main as JarJarMain
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class JarJar extends DefaultTask {
    @InputFile File inputFile
    @OutputFile File outputFile

    @Input def rules = [:]
    @Input def keeps = []

    @TaskAction
    void nativeJarJar() {
        try {
            TemporaryFileProvider tmpFileProvider = getServices().get(TemporaryFileProvider);
            File tempRuleFile = tmpFileProvider.createTemporaryFile("jarjar", "rule")
            writeRuleFile(tempRuleFile)

            JarJarMain.main("process", tempRuleFile.absolutePath, inputFile.absolutePath, outputFile.absolutePath)
        } catch (IOException e) {
            throw new GradleException("Unable to execute JarJar task", e);
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