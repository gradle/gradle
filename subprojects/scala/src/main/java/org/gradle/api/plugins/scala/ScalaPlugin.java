/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.plugins.scala;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.scala.ScalaDoc;

import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} which sets up a Scala project.</p>
 *
 * @see ScalaBasePlugin
 */
public class ScalaPlugin implements Plugin<Project> {

    public static final String SCALA_DOC_TASK_NAME = "scaladoc";

    public void apply(Project project) {
        project.getPluginManager().apply(ScalaBasePlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);

        configureScaladoc(project);
    }

    private static void configureScaladoc(final Project project) {
        project.getTasks().withType(ScalaDoc.class, new Action<ScalaDoc>() {
            @Override
            public void execute(ScalaDoc scalaDoc) {
                final SourceSet main = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main");
                scalaDoc.getConventionMapping().map("classpath", new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() throws Exception {
                        ConfigurableFileCollection files = project.files();
                        files.from(main.getOutput());
                        files.from(main.getCompileClasspath());
                        return files;
                    }
                });
                scalaDoc.setSource(InvokerHelper.invokeMethod(main, "getScala", null));
            }
        });
        ScalaDoc scalaDoc = project.getTasks().create(SCALA_DOC_TASK_NAME, ScalaDoc.class);
        scalaDoc.setDescription("Generates Scaladoc for the main source code.");
        scalaDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
    }
}
