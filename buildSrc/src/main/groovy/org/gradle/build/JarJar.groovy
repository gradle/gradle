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

import com.tonicsystems.jarjar.Keep
import com.tonicsystems.jarjar.Rule
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class JarJar extends DefaultTask {
    @InputFiles FileCollection inputFiles
    @OutputFile File outputFile

    def patterns = [] //todo mark these patterns as task input

    @TaskAction
    void nativeJarJar() {
        try {
            File tempRuleFile = createRuleFile()
            JarJarMain.main("process", tempRuleFile .absolutePath, inputFiles.getSingleFile().absolutePath, outputFile.absolutePath)
        } catch (IOException e) {
            throw new GradleException("unable to run jarjar task", e);
        }
    }

    private File createRuleFile() {
        TemporaryFileProvider tmpFileProvider = getServices().get(TemporaryFileProvider);
        File tempRuleFile = tmpFileProvider.createTemporaryFile("jarjar", "jar")
        tempRuleFile.withPrintWriter { writer ->
            patterns.findAll {it instanceof Rule}.each {rule ->
                writer.println("rule ${rule.pattern} ${rule.result}")
            }
        }

        return tempRuleFile;
    }

    void rule(String pattern, String result) {
        patterns << new Rule(pattern: pattern, result: result)
    }

    void keep(String pattern) {
        patterns << new Keep(pattern: pattern)
    }

}