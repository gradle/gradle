/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.declarative.dsl.tooling.builders.r89

import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@TargetGradleVersion(">=8.9")
@ToolingApiVersion('>=8.9')
class DeclarativeDslToolingModelsCrossVersionTest extends ToolingApiSpecification {

    def setup(){
        settingsFile.delete() //we are using a declarative settings file
    }

    @Requires([UnitTestPreconditions.Jdk11OrLater])
    def 'can obtain model containing project schema'() {
        given:
        file("settings.gradle.something") << """
            rootProject.name = "test"
            include(":a")
            include(":b")
        """

        file("a/build.gradle.something") << """
            plugins {
                id("java")
            }
        """

        file("b/build.gradle.something") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(project(\":a\"))
                api(project(\":a\"))
                compileOnly(project(\":a\"))
                runtimeOnly(project(\":a\"))
                testImplementation(project(\":a\"))
                testCompileOnly(project(\":a\"))
            }
        """

        when:
        DeclarativeSchemaModel model = toolingApi.withConnection() { connection -> connection.getModel(DeclarativeSchemaModel.class) }

        then:
        model != null

        def schema = model.getProjectSchema()
        !schema.dataClassesByFqName.isEmpty()
    }

}
