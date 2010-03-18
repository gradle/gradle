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
package org.gradle.api.tasks.scala;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.InputFiles;

import java.io.File;

public class ScalaDefine extends ConventionTask {

    private FileCollection classpath;
    private AntScalaDefine antScalaDefine;

    public ScalaDefine() {
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    public AntScalaDefine getAntScalaDefine() {
        if (antScalaDefine == null) {
            antScalaDefine = new AntScalaDefine(getAnt());
        }
        return antScalaDefine;
    }

    public void setAntScalaDefine(AntScalaDefine antScalaDefine) {
        this.antScalaDefine = antScalaDefine;
    }

    @InputFiles
    public Iterable<File> getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @TaskAction
    protected void defineAntTasks() {
        getAntScalaDefine().execute(getClasspath());
    }

}
