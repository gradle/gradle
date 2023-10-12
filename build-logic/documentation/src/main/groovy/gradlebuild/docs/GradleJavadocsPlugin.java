/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import gradlebuild.basics.BuildEnvironment;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates Javadocs in a particular way.
 *
 * TODO: We should remove the workarounds here and migrate some of the changes here into the Javadoc task proper.
 */
public abstract class GradleJavadocsPlugin implements Plugin<Project> {

    @Inject
    protected abstract FileSystemOperations getFs();

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

        ObjectFactory objects = project.getObjects();
        // TODO: This breaks if version is changed later
        Object version = project.getVersion();

        TaskProvider<Javadoc> javadocAll = tasks.register("javadocAll", Javadoc.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generate Javadocs for all API classes");

            task.setTitle("Gradle API " + version);

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
            options.tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:");
            // TODO: This breaks the provider
            options.links(javadocs.getJavaApi().get().toString(), javadocs.getGroovyApi().get().toString());

            task.source(extension.getDocumentedSource().filter(f -> f.getName().endsWith(".java") || f.getName().endsWith(".kt")));

            task.setClasspath(extension.getClasspath());

            // TODO: This should be in Javadoc task
            DirectoryProperty generatedJavadocDirectory = objects.directoryProperty();
            generatedJavadocDirectory.set(layout.getBuildDirectory().dir("javadoc"));
            task.getOutputs().dir(generatedJavadocDirectory);
            task.getExtensions().getExtraProperties().set("destinationDirectory", generatedJavadocDirectory);
            // TODO: This breaks the provider
            task.setDestinationDir(generatedJavadocDirectory.get().getAsFile());

            if (BuildEnvironment.INSTANCE.getJavaVersion().isJava11Compatible()) {
                // TODO html4 output was removed in Java 13, see https://bugs.openjdk.org/browse/JDK-8215578
                options.addBooleanOption("html4", true);
                options.addBooleanOption("-no-module-directories", true);

                FileSystemOperations fs = getFs();
                //noinspection Convert2Lambda
                task.doLast(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        fs.copy(copySpec -> {
                            // This is a work-around for https://bugs.openjdk.java.net/browse/JDK-8211194. Can be removed once that issue is fixed on JDK's side
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
            task.getReports().getXml().getOutputLocation().set(new File(checkstyle.getReportsDir(), "checkstyle-api.xml"));
        });
    }
}
