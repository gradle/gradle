/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.gradlebuild.docs;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.gradlebuild.BuildEnvironment;

import java.io.File;

/**
 * Generates Javadocs in a particular way.
 *
 * TODO: We should remove the workarounds here and migrate some of the changes here into the Javadoc task proper.
 */
public class GradleJavadocsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        generateJavadocs(project, layout, tasks, extension);
    }

    private void generateJavadocs(Project project, ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        // TODO: Staging directory should be a part of the Javadocs extension
        // TODO: Pull out more of this configuration into the extension if it makes sense
        // TODO: in a typical project, this may need to be the regular javadoc task vs javadocAll
        TaskProvider<Javadoc> javadocAll = tasks.register("javadocAll", Javadoc.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generate Javadocs for all API classes");

            // TODO: This breaks if version is changed later
            task.setTitle("Gradle API " + project.getVersion());

            Javadocs javadocs = extension.getJavadocs();

            // TODO: This should be part of Javadoc task
            task.getInputs().file(javadocs.getJavadocCss())
                    .withPropertyName("stylesheetFile")
                    .withPathSensitivity(PathSensitivity.NAME_ONLY);

            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
            options.setEncoding("utf-8");
            options.setDocEncoding("utf-8");
            options.setCharSet("utf-8");

            // TODO: This would be better to model as separate options
            options.addStringOption("Xdoclint:syntax,html,reference", "-quiet");
            // TODO: This breaks the provider
            options.addStringOption("stylesheetfile", javadocs.getJavadocCss().get().getAsFile().getAbsolutePath());
            options.addStringOption("source", "8");
            // TODO: This breaks the provider
            options.links(javadocs.getJavaApi().get().toString(), javadocs.getGroovyApi().get().toString());

            task.source(extension.getDocumentedSource());

            task.setClasspath(extension.getClasspath());

            // TODO: This should be in Javadoc task
            DirectoryProperty generatedJavadocDirectory = project.getObjects().directoryProperty();
            generatedJavadocDirectory.set(layout.getBuildDirectory().dir("javadoc"));
            task.getOutputs().dir(generatedJavadocDirectory);
            task.getExtensions().getExtraProperties().set("destinationDirectory", generatedJavadocDirectory);
            // TODO: This breaks the provider
            task.setDestinationDir(generatedJavadocDirectory.get().getAsFile());

            if (BuildEnvironment.INSTANCE.getJavaVersion().isJava11Compatible()) {
                options.addBooleanOption("html4", true);
                options.addBooleanOption("-no-module-directories", true);

                //noinspection Convert2Lambda
                task.doLast(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        project.copy(copySpec -> {
                            // Commit http://hg.openjdk.java.net/jdk/jdk/rev/89dc31d7572b broke use of JSZip (https://bugs.openjdk.java.net/browse/JDK-8214856)
                            // fixed in Java 12 by http://hg.openjdk.java.net/jdk/jdk/rev/b4982a22926b
                            // TODO: Remove this script.js workaround when we distribute Gradle using JDK 12 or higher
                            copySpec.from(extension.getSourceRoot().dir("js/javadoc"));

                            // This is a work-around for https://bugs.openjdk.java.net/browse/JDK-8211194. Can be removed once that issue is fixed on JDK"s side
                            // Since JDK 11, package-list is missing from javadoc output files and superseded by element-list file, but a lot of external tools still need it
                            // Here we generate this file manually
                            copySpec.from(generatedJavadocDirectory.file("element-list"), sub -> {
                                sub.rename(t -> "package-list");
                            });
                            copySpec.into(generatedJavadocDirectory);
                        });
                    }
                });
            }
        });

        extension.javadocs(javadocs -> {
            javadocs.getJavadocCss().convention(extension.getSourceRoot().file("css/javadoc.css"));

            // TODO: destinationDirectory should be part of Javadoc
            javadocs.getRenderedDocumentation().from(javadocAll.flatMap(task -> (DirectoryProperty) task.getExtensions().getExtraProperties().get("destinationDirectory")));
        });

        CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
        tasks.register("checkstyleApi", Checkstyle.class, task -> {
            task.source(extension.getDocumentedSource());
            // TODO: This is ugly
            task.setConfig(project.getResources().getText().fromFile(checkstyle.getConfigDirectory().file("checkstyle-api.xml")));
            task.setClasspath(layout.files());
            task.getReports().getXml().setDestination(new File(checkstyle.getReportsDir(), "checkstyle-api.xml"));
        });
    }
}
