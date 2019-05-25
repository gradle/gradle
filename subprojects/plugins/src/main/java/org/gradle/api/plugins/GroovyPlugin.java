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

package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.javadoc.Groovydoc;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to provide support for compiling and documenting Groovy
 * source files.</p>
 */
public class GroovyPlugin implements Plugin<Project> {
    public static final String GROOVYDOC_TASK_NAME = "groovydoc";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(GroovyBasePlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);
        configureGroovydoc(project);
    }

    private void configureGroovydoc(final Project project) {
        project.getTasks().register(GROOVYDOC_TASK_NAME, Groovydoc.class, new Action<Groovydoc>() {
            @Override
            public void execute(Groovydoc groovyDoc) {
                groovyDoc.setDescription("Generates Groovydoc API documentation for the main source code.");
                groovyDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);

                JavaPluginConvention convention = project.getConvention().getPlugin(JavaPluginConvention.class);
                SourceSet sourceSet = convention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                groovyDoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));

                GroovySourceSet groovySourceSet = new DslObject(sourceSet).getConvention().getPlugin(GroovySourceSet.class);
                groovyDoc.setSource(groovySourceSet.getGroovy());
            }
        });
    }
}
