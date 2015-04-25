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
package org.gradle.api.plugins.mirah;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.mirah.MirahDoc
import org.gradle.api.plugins.JavaBasePlugin

public class MirahPlugin implements Plugin<Project> {
    // tasks
    public static final String MIRAH_DOC_TASK_NAME = "mirahdoc";

    public void apply(Project project) {
        project.pluginManager.apply(MirahBasePlugin);
        project.pluginManager.apply(JavaPlugin);

        configureMirahdoc(project);
    }

    private void configureMirahdoc(final Project project) {
        project.getTasks().withType(MirahDoc.class) {MirahDoc mirahDoc ->
            mirahDoc.conventionMapping.classpath = { project.sourceSets.main.output + project.sourceSets.main.compileClasspath }
            mirahDoc.source = project.sourceSets.main.mirah
        }
        MirahDoc mirahDoc = project.tasks.create(MIRAH_DOC_TASK_NAME, MirahDoc.class)
        mirahDoc.description = "Generates Mirahdoc for the main source code.";
        mirahDoc.group = JavaBasePlugin.DOCUMENTATION_GROUP
    }
}
