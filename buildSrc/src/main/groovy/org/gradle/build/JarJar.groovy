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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.FileCollection
import com.tonicsystems.jarjar.JarJarTask as JarJarAntTask
import com.tonicsystems.jarjar.Rule;
import com.tonicsystems.jarjar.Keep;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.ZipFileSet

class JarJar extends DefaultTask {
    @InputFiles FileCollection inputFiles
    @OutputFile outputFile

    def rules = [] //todo: mark as input property
    def keeps = [] //todo: mark as input property

    @TaskAction
    void nativeJarJar() {
        JarJarAntTask jjTask = new JarJarAntTask();
        jjTask.setProject(new Project());
        jjTask.setDestFile(new File("jaroutput.jar"));

        addInputLibs(jjTask)

        rules.each { rule ->
            jjTask.addConfiguredRule(rule);
        }
        rules.each { keep ->
            jjTask.addConfiguredRule(keep);
        }
        jjTask.execute();
    }

    private void addInputLibs(JarJarAntTask jjTask) {
        inputFiles.each { inputFile ->
            ZipFileSet zipFileSet = new ZipFileSet();
            zipFileSet.setSrc(inputFile);
            jjTask.addZipfileset(zipFileSet);
        }
    }

    void addRule(String pattern, String result) {
        rules.add(new Rule(pattern: pattern, result: result))
    }

    void keep(String pattern) {
        this.keeps << new Keep(pattern: pattern)
    }
}