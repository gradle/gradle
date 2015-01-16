/*
 * Copyright 2015 the original author or authors.
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

package sample.markdown

import org.gradle.api.Action
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.model.RuleSource
import org.gradle.platform.base.BinaryTasks
import org.gradle.platform.base.LanguageType
import org.gradle.platform.base.LanguageTypeBuilder
import org.gradle.model.collection.CollectionBuilder
import org.gradle.api.Task
import sample.documentation.DocumentationBinary

class MarkdownPlugin extends RuleSource {
    @LanguageType
    void declareMarkdownLanguage(LanguageTypeBuilder<MarkdownSourceSet> builder) {
        builder.setLanguageName("Markdown")
        builder.defaultImplementation(DefaultMarkdownSourceSet)
    }

    @BinaryTasks
    void createMarkdownHtmlCompilerTasks(CollectionBuilder<Task> tasks, final DocumentationBinary binary, @Path("buildDir") final File buildDir) {
        for (final MarkdownSourceSet markdownSourceSet : binary.getSource().withType(MarkdownSourceSet.class)) {
            final String taskName = binary.getName() + markdownSourceSet.getName().capitalize() + "HtmlCompile"
            final File htmlOutputDirectory = new File(buildDir, "${binary.name}/src/${markdownSourceSet.name}");
            tasks.create(taskName, MarkdownHtmlCompile.class, new Action<MarkdownHtmlCompile>() {
                @Override
                public void execute(MarkdownHtmlCompile markdownHtmlCompile) {
                    markdownHtmlCompile.setSource(markdownSourceSet.getSource());
                    markdownHtmlCompile.setDestinationDir(htmlOutputDirectory);
                    binary.add(markdownSourceSet.name, markdownHtmlCompile)
                }
            });
        }
    }
}