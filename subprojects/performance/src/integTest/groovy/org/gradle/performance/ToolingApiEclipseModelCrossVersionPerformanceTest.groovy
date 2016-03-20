/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.categories.Experiment
import org.gradle.performance.categories.ToolingApiPerformanceTest
import org.gradle.tooling.model.HasGradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.measure.Duration.millis

@Category([ToolingApiPerformanceTest, Experiment])
class ToolingApiEclipseModelCrossVersionPerformanceTest extends AbstractToolingApiCrossVersionPerformanceTest {

    @Unroll
    def "building #ide model for a #template project"() {
        given:

        experiment(template, "get $template ${modelClass.simpleName} model") {
            warmUpCount = 3
            invocationCount = 10
            maxExecutionTimeRegression = millis(maxRegressionTime)
            action {
                def model = getModel(tapiClass(modelClass))
                // we must actually do something to highlight some performance issues
                collectTasks(model, [])
            }
        }

        when:
        def results = performMeasurements()

        then:
        noExceptionThrown()

        where:
        template                 | modelClass     | maxRegressionTime
        "smallOldJava"           | EclipseProject | 20
        "smallOldJava"           | IdeaProject    | 20
        "mediumOldJava"          | EclipseProject | 100
        "mediumOldJava"          | IdeaProject    | 100
        "bigOldJava"             | EclipseProject | 100
        "bigOldJava"             | IdeaProject    | 100
        "lotDependencies"        | EclipseProject | 400
        "lotDependencies"        | IdeaProject    | 400
        "lotProjectDependencies" | EclipseProject | 400
        "lotProjectDependencies" | IdeaProject    | 400
        ide = modelClass == IdeaProject ? 'IDEA' : 'Eclipse'
    }

    private String settings(int size) {
        (0..size).collect {
            "include 'project${it + 1}'"
        }.join('\n')
    }

    private static void collectTasks(def elm, List<String> tasks) {
        if (elm instanceof HasGradleProject) {
            elm.gradleProject.tasks.collect(tasks) { it.name }
        }
        elm.children?.each {
            collectTasks(it, tasks)
        }
    }
}
