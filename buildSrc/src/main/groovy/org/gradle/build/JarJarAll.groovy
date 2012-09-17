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
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.tasks.*

//TODO SF duplicated with JarJar, merge
class JarJarAll extends DefaultTask {
    @InputFiles FileCollection inputJars
    @OutputDirectory File outputDir

    @Input def rules = [:]
    @Input def keeps = []

    @TaskAction
    void nativeJarJar() {
        try {
            TemporaryFileProvider tmpFileProvider = getServices().get(TemporaryFileProvider);
            File tempRuleFile = tmpFileProvider.createTemporaryFile("jarjar", "rule")
            writeRuleFile(tempRuleFile)

            if (inputJars.empty) {
                throw new GradleException("Unable to execute JarJar task because there are no input jars.");
            }

            for(file in inputJars) {
                JarJarMain.main("process", tempRuleFile.absolutePath, file.absolutePath, new File(outputDir, "jarjar-$file.name").absolutePath)
            }
        } catch (Exception e) {
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