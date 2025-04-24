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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;

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

            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
            options.setEncoding("utf-8");
            options.setDocEncoding("utf-8");
            options.setCharSet("utf-8");

            options.addBooleanOption("-allow-script-in-comments", true);
            options.setHeader("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/stackoverflow-light.min.css\">" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/kotlin.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/groovy.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/java.min.js\"></script>" +
                "<script>hljs.highlightAll();</script>" +
                "<link href=\"https://fonts.cdnfonts.com/css/dejavu-sans\" rel=\"stylesheet\">" +
                "<link href=\"https://fonts.cdnfonts.com/css/dejavu-serif\" rel=\"stylesheet\">" +
                "<link href=\"https://fonts.cdnfonts.com/css/dejavu-sans-mono\" rel=\"stylesheet\">"
            );

            // TODO: This would be better to model as separate options
            options.addStringOption("Xdoclint:syntax,html", "-quiet");
            // TODO: This breaks the provider
            options.addStringOption("-add-stylesheet", javadocs.getJavadocCss().get().getAsFile().getAbsolutePath());
            options.addStringOption("source", "8");
            options.tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:");
            // TODO: This breaks the provider
            options.links(javadocs.getJavaApi().get().toString(), javadocs.getGroovyApi().get().toString());

            task.source(extension.getDocumentedSource().filter(f -> f.getName().endsWith(".java")));

            task.setClasspath(extension.getClasspath());

            // TODO: This should be in Javadoc task
            DirectoryProperty generatedJavadocDirectory = objects.directoryProperty();
            generatedJavadocDirectory.set(layout.getBuildDirectory().dir("javadoc"));
            task.getOutputs().dir(generatedJavadocDirectory);
            task.getExtensions().getExtraProperties().set("destinationDirectory", generatedJavadocDirectory);
            // TODO: This breaks the provider
            task.setDestinationDir(generatedJavadocDirectory.get().getAsFile());

        });

        extension.javadocs(javadocs -> {
            javadocs.getJavadocCss().convention(extension.getSourceRoot().file("css/javadoc-dark-theme.css"));

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
