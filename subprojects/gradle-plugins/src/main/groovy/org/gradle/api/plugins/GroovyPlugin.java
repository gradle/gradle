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
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.javadoc.Groovydoc;

import static org.gradle.api.plugins.JavaPlugin.COMPILE_CONFIGURATION_NAME;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to provide support for compiling and documenting Groovy
 * source files.</p>
 *
 * @author Hans Dockter
 */
public class GroovyPlugin implements Plugin<Project> {
    public static final String GROOVYDOC_TASK_NAME = "groovydoc";

    public void apply(Project project) {
        project.getPlugins().apply(GroovyBasePlugin.class);
        project.getPlugins().apply(JavaPlugin.class);

        project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME).extendsFrom(
                project.getConfigurations().getByName(GroovyBasePlugin.GROOVY_CONFIGURATION_NAME)
        );
        configureGroovydoc(project);
    }

    private void configureGroovydoc(final Project project) {
        Groovydoc groovyDoc = project.getTasks().add(GROOVYDOC_TASK_NAME, Groovydoc.class);
        groovyDoc.setDescription("Generates the groovydoc for the main source code.");
        groovyDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
        groovyDoc.setSource(mainGroovy(project.getConvention()).getGroovy());
    }

    private SourceSet main(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    }

    private GroovySourceSet mainGroovy(Convention convention) {
        return ((DynamicObjectAware) main(convention)).getConvention().getPlugin(GroovySourceSet.class);
    }

}
