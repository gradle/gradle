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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.tooling.fixture.ToolingApiAdditionalClasspathProvider


/**
 * Provides TAPI client additional classpath as found in Kotlin IDEs.
 */
class KotlinDslToolingModelsClasspathProvider implements ToolingApiAdditionalClasspathProvider {

    @Override
    List<File> additionalClasspathFor(GradleDistribution distribution) {
        distribution.gradleHomeDir.file("lib").listFiles().findAll { file ->
            file.name.startsWith("gradle-kotlin-dsl-") || file.name.startsWith("kotlin-stdlib-")
        }.tap { classpath ->
            assert classpath.size() >= 3
        }
    }
}
