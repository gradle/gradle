/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.executer

/**
 * by Szczepan Faber, created at: 12/21/12
 */
class OutputScraper {
    List<String> evaluatedProjects
    final static String EVAL_NOT_FOUND = "No evaluated projects found. Make sure you run the build with '-i'."
    final static String ALL_EVAL_ERROR = "The gradle.projectsEvaluated hook must not be fired before all projects have been evaluated."

    OutputScraper(String output) {
        this(getEvaluatedProjects(output))
    }
    OutputScraper(List<String> evaluatedProjects) {
        this.evaluatedProjects = evaluatedProjects
    }

    void assertProjectsEvaluated(Collection projectPaths) {
        if (!projectPaths.empty && evaluatedProjects.empty) {
            throw new AssertionError(EVAL_NOT_FOUND)
        }
        assert evaluatedProjects == projectPaths as List
    }

    static List<String> getEvaluatedProjects(String output) {
        def evaluatedProjects = []
        def allEvaluated = false
        output.eachLine {
            if (it.startsWith('All projects evaluated.')) {
                allEvaluated = true
            } else if (it.startsWith('Evaluating root project')) {
                assertNotEvaluated(allEvaluated, it)
                evaluatedProjects << ':'
            } else {
                def m = it =~ /Evaluating project '(.*)' using build file.*/
                if (m) {
                    assertNotEvaluated(allEvaluated, it)
                    evaluatedProjects << m.group(1)
                }
            }
        }
        evaluatedProjects
    }

    final static void assertNotEvaluated(boolean allEvaluated, String line) {
        assert !allEvaluated : ALL_EVAL_ERROR + " Found this line after evaluation of all projects: $line"
    }
}
