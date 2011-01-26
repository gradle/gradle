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


import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import groovy.text.SimpleTemplateEngine

/**
 * <p>A {@link org.gradle.api.Task} for creating OS dependent startScripts.</p>
 *
 * @author Rene Groeschke
 */
class CreateStartScripts extends ConventionTask{

    File outputDir

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

    @OutputFile
    public File getOutputDir(){
        if(!outputDir){
            this.outputDir = new File(project.buildDir, "scripts");
        }
        return outputDir;
    }


    @TaskAction
    public void generate(){
        getOutputDir().mkdirs();

        //ref all files in classpath
        def applicationHome = "${project.getName().toUpperCase()}_HOME"
        def unixLibPath = "\$$applicationHome/lib/"
        StringBuffer unixClasspath = new StringBuffer();

        def windowsLibPath = "%$applicationHome%\\lib\\"
        StringBuffer windowsClasspath = new StringBuffer();

        classpath.each {
            unixClasspath<<"$unixLibPath${it.name}:"
            windowsClasspath << "$windowsLibPath${it.name};"
        }

        generateLinuxStartScript([project_name:project.name, mainClassName:getMainClassName(), classpath:unixClasspath])
        generateWindowsStartScript([project_name:project.name, mainClassName:getMainClassName(), classpath:windowsClasspath])
    }

    void generateWindowsStartScript(def binding) {
        def engine = new SimpleTemplateEngine();
        String windowsTemplate = CreateStartScripts.getResourceAsStream('windowsStartScript.txt').text
        def windowsOutput = engine.createTemplate(windowsTemplate).make(binding)

        def windowsScript = new File(outputDir, "${project.name}.bat");
        windowsScript.withWriter {writer ->
            writer.write(transformIntoWindowsNewLines(windowsOutput))
        }
    }

    void generateLinuxStartScript(def binding) {
        def engine = new SimpleTemplateEngine();
        String unixTemplate = CreateStartScripts.getResourceAsStream('unixStartScript.txt').text
        def linuxOutput = engine.createTemplate(unixTemplate).make(binding)

        def unixScript = new File(outputDir, project.name);
        unixScript.withWriter {writer ->
            writer.write(linuxOutput)
        }
        unixScript.setExecutable(true);
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
