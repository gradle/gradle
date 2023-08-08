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

package org.gradle.kotlin.dsl.tooling.builders.r41

import org.gradle.integtests.tooling.fixture.TargetGradleVersion

import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptTemplateModel


@TargetGradleVersion(">=4.1")
class KotlinBuildScriptTemplateModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "can load script template using classpath model"() {

        when:
        def model = loadToolingModel(KotlinBuildScriptTemplateModel)

        then:
        loadClassesFrom(
            model.classPath,
            "org.gradle.kotlin.dsl.KotlinBuildScript",
            "org.gradle.kotlin.dsl.resolver.KotlinBuildScriptDependenciesResolver")
    }

    private static void loadClassesFrom(List<File> classPath, String... classNames) {
        classLoaderFor(classPath).withCloseable { loader ->
            classNames.each {
                loader.loadClass(it)
            }
        }
    }

    private static URLClassLoader classLoaderFor(List<File> classPath) {
        new URLClassLoader(classPath.collect { it.toURI().toURL() } as URL[])
    }
}
