/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.application;


import groovy.text.SimpleTemplateEngine
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

/**
 * <p>A {@link org.gradle.api.Task} for creating OS dependent startScripts.</p>
 *
 * @author Rene Groeschke
 */
class CreateStartScripts extends ConventionTask{

    @Input
    private String mainClassName;

    public void setMainClassName(String mainClassName){
        this.mainClassName = mainClassName;
    }

    public String getMainClassName(){
        return mainClassName;
    }

    @InputFiles
    FileCollection classpath;

    @TaskAction
    public void generate(){
        def outputDir = new File(project.buildDir, "scripts");
        outputDir.mkdirs();

        generateUnixStartScript(outputDir);
    }

    private void generateUnixStartScript(File outputDir) {
        //ref all files in classpath
        def applicationHome = "${project.getName().toUpperCase()}_HOME"
        def libPath = "$applicationHome/lib/"
        StringBuffer unixClasspath = new StringBuffer();
        classpath.each { unixClasspath<<"$libPath${it.name};" }


        String unixTemplate = CreateStartScripts.getResourceAsStream('unixStartScript.txt').text
        def binding = ["project_name":project.name, unixClasspath:unixClasspath, mainClassName:getMainClassName()]
        def engine = new SimpleTemplateEngine()
        def output = engine.createTemplate(unixTemplate).make(binding)

        new File(outputDir, project.name).withWriter {writer ->
            writer.write(output)
        }
    }

    static String transformIntoWindowsNewLines(String s) {
        StringWriter writer = new StringWriter()
        s.toCharArray().each {c ->
            if (c == '\n') {
                writer.write('\r')
                writer.write('\n')
            } else if (c != '\r') {
                writer.write(c);
            }
        }
        writer.toString()
    }
}
