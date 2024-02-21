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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import gradlebuild.docs.dsl.docbook.AssembleDslDocTask;
import gradlebuild.docs.dsl.source.ExtractDslMetaDataTask;

/**
 * Generates DSL reference material using Docbook and some homegrown class parsing.
 *
 * TODO: It would be nice to replace the Docbook portion of this with Asciidoc so that it could be
 * generated in the same way as the user manual with cross-links between them.
 */
public class GradleDslReferencePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();
        ObjectFactory objects = project.getObjects();

        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        generateDslReference(project, layout, tasks, objects, extension);
    }

    private void generateDslReference(Project project, ProjectLayout layout, TaskContainer tasks, ObjectFactory objects, GradleDocumentationExtension extension) {
        DslReference dslReference = extension.getDslReference();

        TaskProvider<ExtractDslMetaDataTask> dslMetaData = tasks.register("dslMetaData", ExtractDslMetaDataTask.class, task -> {
            task.source(extension.getDocumentedSource());
            task.getDestinationFile().convention(dslReference.getStagingRoot().file("dsl-meta-data.bin"));
        });

        TaskProvider<AssembleDslDocTask> dslDocbook = tasks.register("dslDocbook", AssembleDslDocTask.class, task -> {
            task.getSourceFile().convention(dslReference.getRoot().file("dsl.xml"));
            task.getPluginsMetaDataFile().convention(dslReference.getRoot().file("plugins.xml"));
            task.getClassDocbookDirectory().convention(dslReference.getRoot());
            task.getClassMetaDataFile().convention(dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile));

            task.getDestFile().convention(dslReference.getStagingRoot().file("dsl.xml"));
            task.getLinksFile().convention(dslReference.getStagingRoot().file("api-links.bin"));
        });

        TaskProvider<UserGuideTransformTask> dslStandaloneDocbook = tasks.register("dslStandaloneDocbook", UserGuideTransformTask.class, task -> {
            task.getVersion().convention(project.provider(() -> project.getVersion().toString()));
            task.getSourceFile().convention(dslDocbook.flatMap(AssembleDslDocTask::getDestFile));
            task.getLinksFile().convention(dslDocbook.flatMap(AssembleDslDocTask::getLinksFile));

            task.getDsldocUrl().convention("../dsl");
            task.getJavadocUrl().convention("../javadoc");
            task.getWebsiteUrl().convention("https://gradle.org");

            task.getDestFile().convention(dslReference.getStagingRoot().file("index.xml"));
        });

        Configuration userGuideTask = project.getConfigurations().create("userGuideTask");
        Configuration userGuideStyleSheetConf = project.getConfigurations().create("userGuideStyleSheets");

        TaskProvider<Docbook2Xhtml> dslHtml = tasks.register("dslHtml", Docbook2Xhtml.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generates DSL reference HTML documentation.");
            task.onlyIf(t -> !extension.getQuickFeedback().get());

            task.source(dslStandaloneDocbook);
            task.getStylesheetDirectory().convention(dslReference.getStylesheetDirectory());
            task.getStylesheetHighlightFile().convention(dslReference.getHighlightStylesheet());
            task.getDocbookStylesheets().from(userGuideStyleSheetConf);

            task.getClasspath().from(userGuideTask);

            task.getDestinationDirectory().convention(dslReference.getStagingRoot().dir("dsl"));
        });

        extension.dslReference(dslRef -> {
            // DSL ref has custom javascript
            ConfigurableFileTree js = objects.fileTree();
            js.from(dslReference.getRoot());
            js.include("*.js");
            dslRef.getResources().from(js);

            dslRef.getResources().from(extension.getCssFiles());

            dslRef.getRoot().convention(extension.getSourceRoot().dir("dsl"));
            dslRef.getStylesheetDirectory().convention(extension.getSourceRoot().dir("stylesheets"));
            dslRef.getHighlightStylesheet().convention(dslRef.getStylesheetDirectory().file("custom-highlight/custom-xslthl-config.xml"));

            dslRef.getStagingRoot().convention(extension.getStagingRoot().dir("dsl"));

            dslRef.getGeneratedMetaDataFile().convention(dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile));

            dslRef.getRenderedDocumentation().from(dslRef.getResources());
            dslRef.getRenderedDocumentation().from(dslHtml.flatMap(Docbook2Xhtml::getDestinationDirectory));
        });
    }
}
