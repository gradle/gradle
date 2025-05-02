/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.plugin.devel.tasks

import groovy.transform.CompileStatic
import org.gradle.api.problems.Severity
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer
import org.gradle.plugin.devel.tasks.internal.ValidationProblemSerialization

@CompileStatic
class TaskValidationReportFixture {
    public static final String PROBLEM_SEPARATOR = "\n--------\n"
    private final File reportFile

    TaskValidationReportFixture(File reportFile) {
        this.reportFile = reportFile
    }

    void verify(Map<String, Severity> messages) {
        def expectedReportContents = messages
            .collect { message, severity ->
                "$severity: $message"
            }
            .join(PROBLEM_SEPARATOR)
            .replaceAll("\n+", "\n")
        def reportText =
            ValidationProblemSerialization.parseMessageList(reportFile.text)
                .collect { it.definition.severity.toString() + ": " + TypeValidationProblemRenderer.renderMinimalInformationAbout(it) }
                .sort()
                .join(PROBLEM_SEPARATOR)
                .replaceAll("\r\n", "\n")
                .replaceAll("\n+", "\n")


        def actualText = reportText
        assert actualText == expectedReportContents
    }
}
