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



package org.gradle.api.plugins.antlr

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.DefaultJavaExecAction
import org.gradle.process.internal.JavaExecAction

/**
 * @author Strong Liu <stliu@hibernate.org>
 */
class Antlr3Task extends ConventionTask {
    /**
     * Class path holding the ANTLR 3 library.
     */
    @InputFiles
    FileCollection antlr3Classpath
    @OutputDirectory
    File outputDirectory
    @InputFiles
    Set<File> grammarFiles
    List args = []
    
    File baseSourcePath

    final private static String MAIN = "org.antlr.Tool"

    private final JavaExecAction javaExecHandleBuilder;

    public Antlr3Task() {
        javaExecHandleBuilder = new DefaultJavaExecAction(project.fileResolver);
    }


    @TaskAction
    public void generate() {
        javaExecHandleBuilder.setMain(MAIN)
        javaExecHandleBuilder.classpath(getAntlr3Classpath())
        javaExecHandleBuilder.args(getArgs())
        javaExecHandleBuilder.setWorkingDir(baseSourcePath)
        javaExecHandleBuilder.execute();
    }
}
