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
package org.gradle.api.plugins;


import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec

 /**
 * <p>A {@link Convention} used for the ApplicationPlugin.</p>
 *
 * @author Rene Groeschke
 */
public class ApplicationPluginConvention {

    /**
     * The full qualified name of the main class.
     */
    String mainClassName;

    /**
     * The path of the installation directory.
     */
    String installDirPath;

    private final Project project;

    public ApplicationPluginConvention(final Project project){
        this.project = project;
        this.installDirPath = project.file("build/install")
    }

    /**
     * Sets the full qualified name of the main class of an application.
     */
    public void setMainClassName(String mainClassName){
        this.mainClassName = mainClassName;
        JavaExec runTask = project.getTasks().withType(JavaExec.class).find{task ->
            task.name == ApplicationPlugin.TASK_RUN_NAME};
        runTask.main = mainClassName;
    }
}
