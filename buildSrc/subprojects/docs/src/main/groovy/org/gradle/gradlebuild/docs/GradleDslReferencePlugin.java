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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.build.docs.Docbook2Xhtml;
import org.gradle.build.docs.UserGuideTransformTask;
import org.gradle.build.docs.dsl.docbook.AssembleDslDocTask;
import org.gradle.build.docs.dsl.source.ExtractDslMetaDataTask;
import org.gradle.build.docs.dsl.source.GenerateDefaultImportsTask;

import java.util.ArrayList;
import java.util.List;

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
        // TODO: Is this needed still?
        TaskProvider<Sync> css = tasks.register("css", Sync.class, task -> {
            task.into(layout.getBuildDirectory().dir("css"));
            task.from(extension.getDocumentationSourceRoot().dir("css"));
            task.include("*.css");
            task.include("*.svg");
        });

//        def imageFiles = fileTree(userguideSrcDir) {
//            include "img/*.png"
//            include "img/*.gif"
//            include "img/*.jpg"
//        }
//        def resourceFiles = imageFiles + cssFiles

        Configuration userGuideStyleSheetConf = project.getConfigurations().create("userGuideStyleSheets");
//        TaskProvider<Sync> userGuideStyleSheets = tasks.register("userguideStyleSheets", Sync.class, task -> {
//            File stylesheetsDir = new File(srcDocsDir, "stylesheets")
//            into new File(buildDir, "stylesheets")
//            from(stylesheetsDir) {
//                include "**/*.xml"
//                include "*.xsl"
//            }
//            from(cssFiles)
//            from({zipTree(userGuideStyleSheetConf.singleFile)}) {
//                // Remove the prefix
//                eachFile {
//                    fcd -> fcd.path = fcd.path.replaceFirst("^docbook-xsl-[0-9\\.]+/", "")
//                }
//            }
//        });

        DslReference dslReference = extension.getDslReference();

        TaskProvider<ExtractDslMetaDataTask> dslMetaData = tasks.register("dslMetaData", ExtractDslMetaDataTask.class, task -> {
            task.source(extension.getSource());
            // TODO: Does this path matter?
            task.getDestinationFile().convention(layout.getBuildDirectory().file("generated-dsl/dsl-meta-data.bin"));
        });

        TaskProvider<AssembleDslDocTask> dslDocbook = tasks.register("dslDocbook", AssembleDslDocTask.class, task -> {
            task.getSources().from(dslReference.getRoot());
            task.getSourceFile().convention(dslReference.getRoot().file("dsl.xml"));
            task.getClassDocbookDirectory().convention(dslReference.getRoot());
            task.getClassMetaDataFile().convention(dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile));
            task.getPluginsMetaDataFile().convention(dslReference.getRoot().file("plugins.xml"));

            // TODO: Do these paths matter?
            task.getDestFile().convention(layout.getBuildDirectory().file("generated-dsl/dsl.xml"));
            task.getLinksFile().convention(layout.getBuildDirectory().file("generated-dsl/api-links.bin"));
        });

        TaskProvider<UserGuideTransformTask> dslStandaloneDocbook = tasks.register("dslStandaloneDocbook", UserGuideTransformTask.class, task -> {
            task.getVersion().convention(project.provider(() -> project.getVersion().toString()));
            task.getSourceFile().convention(dslDocbook.flatMap(AssembleDslDocTask::getDestFile));
            task.getLinksFile().convention(dslDocbook.flatMap(AssembleDslDocTask::getLinksFile));

            task.getDsldocUrl().convention("../dsl");
            task.getJavadocUrl().convention("../javadoc");
            task.getWebsiteUrl().convention("https://gradle.org");

            // TODO: Do these paths matter?
            task.getDestFile().convention(layout.getBuildDirectory().file("generated-dsl/dsl-standalone.xml"));
        });

        Configuration userGuideTask = project.getConfigurations().create("userGuideTask");

        TaskProvider<Docbook2Xhtml> dslHtml = tasks.register("dslHtml", Docbook2Xhtml.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generates DSL reference HTML documentation.");

            task.source(dslStandaloneDocbook);
            task.getStylesheetFile().convention(dslReference.getStylesheet());
            task.getStylesheetHighlightFile().convention(dslReference.getHighlightStylesheet());

            task.getResources().from(dslReference.getResources());
            task.getClasspath().from(userGuideTask);

            task.getDestinationDirectory().convention(layout.getBuildDirectory().dir("dsl"));
        });

        TaskProvider<GenerateDefaultImportsTask> defaultImports = tasks.register("defaultImports", GenerateDefaultImportsTask.class, task -> {
            task.getMetaDataFile().convention(dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile));
            task.getImportsDestFile().convention(layout.getBuildDirectory().file("generated-imports/default-imports.txt"));
            task.getMappingDestFile().convention(layout.getBuildDirectory().file("generated-imports/api-mapping.txt"));

            List<String> excludedPackages = new ArrayList<>();
            // These are part of the API, but not the DSL
            excludedPackages.add("org.gradle.tooling.**");
            excludedPackages.add("org.gradle.testfixtures.**");

            // Tweak the imports due to some inconsistencies introduced before we automated the default-imports generation
            excludedPackages.add("org.gradle.plugins.ide.eclipse.model");
            excludedPackages.add("org.gradle.plugins.ide.idea.model");
            excludedPackages.add("org.gradle.api.tasks.testing.logging");

            // TODO - rename some incubating types to remove collisions and then remove these exclusions
            excludedPackages.add("org.gradle.plugins.binaries.model");

            // Exclude classes that were moved in a different package but the deprecated ones are not removed yet
            excludedPackages.add("org.gradle.platform.base.test");

            task.getExcludedPackages().convention(excludedPackages);
        });

        extension.dslReference(dslRef -> {
            dslRef.getResources().from(css);
            ConfigurableFileTree js = objects.fileTree();
            js.from(dslReference.getRoot());
            js.include("*.js");
            dslRef.getResources().from(js);

            dslRef.getRoot().convention(extension.getDocumentationSourceRoot().dir("dsl"));
            dslRef.getStylesheet().convention(extension.getDocumentationSourceRoot().file("stylesheets/dslHtml.xsl"));
            dslRef.getHighlightStylesheet().convention(extension.getDocumentationSourceRoot().file("stylesheets/custom-highlight/custom-xslthl-config.xml"));

            dslRef.getRenderedDocumentation().convention(dslHtml.flatMap(Docbook2Xhtml::getDestinationDirectory));
        });

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main", main -> {
            // TODO:
            //  sourceSets.main.output.dir generatedResourcesDir, builtBy: [defaultImports, copyReleaseFeatures]
//            main.getOutput().dir(defaultImports);
        });
    }
}
