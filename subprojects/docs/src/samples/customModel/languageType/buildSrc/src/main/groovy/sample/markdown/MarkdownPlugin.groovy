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

import org.gradle.model.Defaults
import org.gradle.model.ModelMap
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.platform.base.LanguageType
import org.gradle.platform.base.LanguageTypeBuilder
import sample.documentation.DocumentationBinary

class MarkdownPlugin extends RuleSource {
    @LanguageType
    void declareMarkdownLanguage(LanguageTypeBuilder<MarkdownSourceSet> builder) {
        builder.setLanguageName("Markdown")
        builder.defaultImplementation(DefaultMarkdownSourceSet)
    }

    @Defaults
    void createMarkdownHtmlCompilerTasks(ModelMap<DocumentationBinary> binaries, @Path("buildDir") File buildDir) {
        binaries.beforeEach { binary ->
            source.withType(MarkdownSourceSet.class) { markdownSourceSet ->
                taskName = binary.name + name.capitalize() + "HtmlCompile"
                outputDir = new File(buildDir, "${binary.name}/src/${name}")
                binary.tasks.create(markdownSourceSet.taskName, MarkdownHtmlCompile) {
                    source = markdownSourceSet.source
                    destinationDir = markdownSourceSet.outputDir
                }
            }
        }
    }
}
