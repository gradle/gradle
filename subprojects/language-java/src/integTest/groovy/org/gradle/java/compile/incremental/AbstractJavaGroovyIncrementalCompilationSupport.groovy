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

package org.gradle.java.compile.incremental

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.integtests.fixtures.FeaturePreviewsFixture

abstract class AbstractJavaGroovyIncrementalCompilationSupport extends AbstractIntegrationSpec {
    CompilationOutputsFixture outputs

    abstract CompiledLanguage getLanguage()

    File source(String... classBodies) {
        return sourceForProject("", classBodies)
    }

    File sourceForProject(String project, String... classBodies) {
        return sourceForLanguageWithSuffixForProject(language, language.name, project, classBodies)
    }

    File sourceWithFileSuffix(String suffix, String... classBodies) {
        return sourceForLanguageWithSuffixForProject(language, suffix, "", classBodies)
    }

    File sourceWithFileSuffixForProject(String suffix, String project, String... classBodies) {
        return sourceForLanguageWithSuffixForProject(language, suffix, project, classBodies)
    }

    File sourceForLanguageForProject(CompiledLanguage language, String project, String... classBodies) {
        return sourceForLanguageWithSuffixForProject(language, language.name, project, classBodies)
    }

    private File sourceForLanguageWithSuffixForProject(CompiledLanguage language, String suffix, String project, String... classBodies) {
        File out = null
        def basePath = project.isEmpty() ? "src/main/$language.name" : "$project/src/main/$language.name"
        for (String body : classBodies) {
            def packageGroup = (body =~ "\\s*package ([\\w.]+).*")
            String packageName = packageGroup.size() > 0 ? packageGroup[0][1] : ""
            String packageFolder = packageName.replaceAll("[.]", "/")
            def className = (body =~ /(?s).*?(?:class|interface|enum) ([\w$]+) .*/)[0][1]
            assert className: "unable to find class name"
            def f
            if (packageFolder.isEmpty()) {
                f = file("$basePath/${className}.$suffix")
            } else {
                f = file("$basePath/${packageFolder}/${className}.$suffix")
            }
            f.createFile()
            f.text = body
            out = f
        }
        out
    }

    protected String getLanguageName() {
        language.name
    }

    def setup() {
        outputs = new CompilationOutputsFixture(file("build/classes"))

        buildFile << """
            apply plugin: '${language.name}'
        """
    }

    def configureGroovyIncrementalCompilation(String allprojectsOrSubprojects = 'allprojects') {
        if (language == CompiledLanguage.GROOVY) {
            buildFile << language.projectGroovyDependencies(allprojectsOrSubprojects)
            buildFile << """
                ${allprojectsOrSubprojects} {
                    tasks.withType(GroovyCompile) {
                        options.incremental = true
                    }
                }
            """
            FeaturePreviewsFixture.enableGroovyCompilationAvoidance(settingsFile)
        }
    }
}
