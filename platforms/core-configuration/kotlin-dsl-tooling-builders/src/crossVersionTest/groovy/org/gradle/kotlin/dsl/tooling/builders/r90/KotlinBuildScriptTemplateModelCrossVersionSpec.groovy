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

package org.gradle.kotlin.dsl.tooling.builders.r90

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptTemplateModel

import java.util.function.Consumer

@TargetGradleVersion(">=4.1")
class KotlinBuildScriptTemplateModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "can load IDE script templates using classpath model"() {

        when:
        def model = loadToolingModel(KotlinBuildScriptTemplateModel)

        and:
        model.classPath.findAll {
            println(it)
        }

        then:
        withLoaderFrom(model.classPath) { loader ->
            loader.loadClass("org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver")
            loader.loadClass("org.gradle.kotlin.dsl.KotlinInitScript")
            loader.loadClass("org.gradle.kotlin.dsl.KotlinSettingsScript")
            loader.loadClass("org.gradle.kotlin.dsl.KotlinBuildScript")
            loader.loadClass("org.gradle.api.HasImplicitReceiver")
            loader.loadClass("org.gradle.kotlin.dsl.KotlinDslStandaloneScriptCompilationConfiguration")
            loader.loadClass("org.gradle.kotlin.dsl.KotlinInitScriptTemplateCompilationConfiguration")
            loader.loadClass("org.gradle.kotlin.dsl.KotlinSettingsScriptTemplateCompilationConfiguration")
            loader.loadClass("org.gradle.kotlin.dsl.KotlinBuildScriptTemplateCompilationConfiguration")
        }
    }

    private static void withLoaderFrom(List<File> classPath, Consumer<ClassLoader> action) {
        new URLClassLoader(classPath.collect { it.toURI().toURL() } as URL[]).withCloseable { loader ->
            action.accept(loader)
        }
    }
}
