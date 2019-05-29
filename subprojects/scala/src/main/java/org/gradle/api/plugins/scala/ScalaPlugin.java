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
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.scala.ScalaDoc;
import org.gradle.language.scala.tasks.AbstractScalaCompile;

import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} which sets up a Scala project.</p>
 *
 * @see ScalaBasePlugin
 */
public class ScalaPlugin implements Plugin<Project> {

    public static final String SCALA_DOC_TASK_NAME = "scaladoc";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ScalaBasePlugin.class);
        project.getPluginManager().apply(JavaPlugin.class);

        final SourceSet main = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main");

        configureScaladoc(project, main);

        final Configuration incrementalAnalysisElements = project.getConfigurations().getByName("incrementalScalaAnalysisElements");
        String compileTaskName = main.getCompileTaskName("scala");
        final TaskProvider<AbstractScalaCompile> compileScala = project.getTasks().withType(AbstractScalaCompile.class).named(compileTaskName);
        final Provider<RegularFile> compileScalaMapping = project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + compileTaskName + ".mapping");
        compileScala.configure(new Action<AbstractScalaCompile>() {
            @Override
            public void execute(AbstractScalaCompile task) {
                task.getAnalysisMappingFile().set(compileScalaMapping);
            }
        });
        incrementalAnalysisElements.getOutgoing().artifact(
            compileScalaMapping, new Action<ConfigurablePublishArtifact>() {
            @Override
            public void execute(ConfigurablePublishArtifact configurablePublishArtifact) {
                configurablePublishArtifact.builtBy(compileScala);
            }
        });
    }

    private static void configureScaladoc(final Project project, final SourceSet main) {
        project.getTasks().withType(ScalaDoc.class).configureEach(new Action<ScalaDoc>() {
            @Override
            public void execute(ScalaDoc scalaDoc) {
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
        project.getTasks().register(SCALA_DOC_TASK_NAME, ScalaDoc.class, new Action<ScalaDoc>() {
            @Override
            public void execute(ScalaDoc scalaDoc) {
                scalaDoc.setDescription("Generates Scaladoc for the main source code.");
                scalaDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
            }
        });
    }
}
