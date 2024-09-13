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

import gradlebuild.basics.Gradle9PropertyUpgradeSupport;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
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

            new JavadocSupport(task).setTitle("Gradle API " + version);

            Javadocs javadocs = extension.getJavadocs();

            // TODO: This should be part of Javadoc task
            task.getInputs().file(javadocs.getJavadocCss())
                .withPropertyName("stylesheetFile")
                .withPathSensitivity(PathSensitivity.NAME_ONLY);

            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
            options.setEncoding("utf-8");
            options.setDocEncoding("utf-8");
            options.setCharSet("utf-8");

            options.addBooleanOption("-allow-script-in-comments", true);
            options.setFooter("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/default.min.css\">" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/kotlin.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/groovy.min.js\"></script>" +
                "<script>hljs.highlightAll();</script>" +
                "<script type=\"text/javascript\">" +
                "const btn = document.querySelector('.theme-toggle');" +
                "const prefersDarkScheme = window.matchMedia('(prefers-color-scheme: dark)');" +
                "const currentTheme = localStorage.getItem('theme');" +
                "if (currentTheme == 'dark') {" +
                "    document.body.classList.toggle('dark-theme');" +
                "} else if (currentTheme == 'light') {" +
                "    document.body.classList.toggle('light-theme');" +
                "}" +
                "btn.addEventListener('click', function () {" +
                "   if (prefersDarkScheme.matches) {" +
                "        document.body.classList.toggle('light-theme');" +
                "        var theme = document.body.classList.contains('light-theme')? 'light' : 'dark';" +
                "    } else {" +
                "        document.body.classList.toggle('dark-theme');" +
                "        var theme = document.body.classList.contains('dark-theme')? 'dark' : 'light';" +
                "    }" +
                "    localStorage.setItem('theme', theme);" +
                "});</script>"
            );

            // TODO: This would be better to model as separate options
            options.addStringOption("Xdoclint:syntax,html", "-quiet");
            // TODO: This breaks the provider
            options.addStringOption("stylesheetfile", javadocs.getJavadocCss().get().getAsFile().getAbsolutePath());
            options.addStringOption("source", "8");
            options.tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:");
            // TODO: This breaks the provider
            options.links(javadocs.getJavaApi().get().toString(), javadocs.getGroovyApi().get().toString());

            task.source(extension.getDocumentedSource().filter(f -> f.getName().endsWith(".java")));

            new JavadocSupport(task).setClasspath(extension.getClasspath());

            // TODO: This should be in Javadoc task
            DirectoryProperty generatedJavadocDirectory = objects.directoryProperty();
            generatedJavadocDirectory.set(layout.getBuildDirectory().dir("javadoc"));
            task.getOutputs().dir(generatedJavadocDirectory);
            task.getExtensions().getExtraProperties().set("destinationDirectory", generatedJavadocDirectory);
            // TODO: This breaks the provider
            new JavadocSupport(task).setDestinationDir(generatedJavadocDirectory.get().getAsFile());

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
            new CheckstyleSupport(task).setClasspath(layout.files());
            task.getReports().getXml().getOutputLocation().set(getCheckstyleOutputLocation(checkstyle, objects));
        });
    }

    /**
     * TODO: Remove this workaround after Gradle 9
     */
    @SuppressWarnings({"ConstantValue", "CastCanBeRemovedNarrowingVariableType"})
    private static Provider<RegularFile> getCheckstyleOutputLocation(CheckstyleExtension checkstyle, ObjectFactory objects) {
        Object reportsDir = checkstyle.getReportsDir();
        if (reportsDir instanceof File) {
            return objects.fileProperty().fileValue(new File((File) reportsDir, "checkstyle-api.xml"));
        } else {
            return ((DirectoryProperty) reportsDir).file("checkstyle-api.xml");
        }
    }

    /**
     * Used to bridge Gradle 8 and Gradle 9 APIs for Gradleception.
     *
     * TODO: Remove this workaround after Gradle 9
     */

    @Deprecated
    private static class JavadocSupport {

        private final Javadoc javadoc;

        public JavadocSupport(Javadoc javadoc) {
            this.javadoc = javadoc;
        }

        public void setTitle(String title) {
            Gradle9PropertyUpgradeSupport.setProperty(javadoc, "setTitle", title);
        }

        public void setClasspath(FileCollection classpath) {
            Gradle9PropertyUpgradeSupport.setProperty(javadoc, "setClasspath", classpath);
        }

        public void setDestinationDir(File destinationDir) {
            Gradle9PropertyUpgradeSupport.setProperty(javadoc, "setDestinationDir", destinationDir);
        }
    }

    /**
     * TODO: Remove this workaround after Gradle 9
     */
    @Deprecated
    private static class CheckstyleSupport {
        private final Checkstyle checkstyle;

        public CheckstyleSupport(Checkstyle checkstyle) {
            this.checkstyle = checkstyle;
        }

        public void setClasspath(FileCollection classpath) {
            Gradle9PropertyUpgradeSupport.setProperty(checkstyle, "setClasspath", classpath);
        }
    }
}
