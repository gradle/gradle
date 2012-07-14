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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.javadoc.Groovydoc;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to provide support for compiling and documenting Groovy
 * source files.</p>
 *
 * @author Hans Dockter
 */
public class GroovyPlugin implements Plugin<Project> {
    public static final String GROOVYDOC_TASK_NAME = "groovydoc";
    public static final String GROOVYDOC_JAR_TASK_NAME = "groovydocJar";
    public static final String GROOVYDOC_ZIP_TASK_NAME = "groovydocZip";

    public void apply(Project project) {
        project.getPlugins().apply(GroovyBasePlugin.class);
        project.getPlugins().apply(JavaPlugin.class);
        configureConfigurations(project);
        configureGroovydoc(project);
        configureGroovydocArchives(project);
    }

    private void configureConfigurations(Project project) {
        project.getConfigurations().getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME).extendsFrom(
                project.getConfigurations().getByName(GroovyBasePlugin.GROOVY_CONFIGURATION_NAME)
        );
    }

    private void configureGroovydoc(final Project project) {
        Groovydoc groovyDoc = project.getTasks().add(GROOVYDOC_TASK_NAME, Groovydoc.class);
        groovyDoc.setDescription("Generates Groovydoc API documentation for the main source code.");
        groovyDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);

        JavaPluginConvention convention = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSet sourceSet = convention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        groovyDoc.setClasspath(sourceSet.getOutput().plus(sourceSet.getCompileClasspath()));

        GroovySourceSet groovySourceSet = new DslObject(sourceSet).getConvention().getPlugin(GroovySourceSet.class);
        groovyDoc.setSource(groovySourceSet.getGroovy());
    }
    
    /**
     * Configures Groovydoc archives: both a JAR and a Zip.  These are not tied into the
     * major lifecycle tasks by default.
     * @param project the project to add the tasks to
     * @param pluginConvention the Java plugin convention for the project
     */
    private void configureGroovydocArchives(final Project project) {
    	TaskOutputs docs = project.getTasks().getByName(GROOVYDOC_TASK_NAME).getOutputs();
    	Jar jar = project.getTasks().add(GROOVYDOC_JAR_TASK_NAME, Jar.class);
    	jar.from(docs);
    	jar.setClassifier("groovydoc");
    	
    	Zip zip = project.getTasks().add(GROOVYDOC_ZIP_TASK_NAME, Zip.class);
    	zip.from(docs);
    	zip.setClassifier("groovydoc");
    }
}
