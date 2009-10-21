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

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.*;

import java.io.File;

public class DirTransformerTask extends DefaultTask {
    private File inputDir;
    private File outputDir;

    @InputDirectory
    public File getInputDir() {
        return inputDir;
    }

    public void setInputDir(File inputDir) {
        this.inputDir = inputDir;
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    public void transform() {
        TestFile inputDir = new TestFile(this.inputDir);
        for (File file : inputDir.listFiles()) {
            TestFile inputFile = new TestFile(file);
            TestFile outputFile = new TestFile(outputDir, inputFile.getName());
            outputFile.write(String.format("[%s]", inputFile.getText()));
        }
    }
}