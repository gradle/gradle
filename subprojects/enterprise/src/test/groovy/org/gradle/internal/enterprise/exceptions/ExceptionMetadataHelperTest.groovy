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

package org.gradle.internal.enterprise.exceptions

import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.execution.MultipleBuildFailures
import org.gradle.groovy.scripts.ScriptCompilationException
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.file.RelativeFilePathResolver
import org.gradle.internal.resource.UriTextResource
import org.gradle.util.Path
import spock.lang.Specification

class ExceptionMetadataHelperTest extends Specification {

    void "extracts exception with multiple cause chain"() {
        given:
        def npe = new NullPointerException("nullness")
        def iae = new IllegalArgumentException("badness", npe)
        def re = new RuntimeException("sadness")
        def mbf = new MultipleBuildFailures([re, iae])

        expect:
        with(ExceptionMetadataHelper.extractCauses(mbf)) {
            size() == 2
            get(0).message == "sadness"
            with(get(1)) {
                message == "badness"
                cause.message == "nullness"
            }
        }
    }

    def "extracts metadata information from LAE"() {
        given:
        def l = 57
        def c = new RuntimeException("!")
        def s = "source"
        def e = new LocationAwareException(c, s, l)

        expect:
        with(ExceptionMetadataHelper.getMetadata(e)) {
            get("sourceDisplayName") == s
            get("lineNumber") == l.toString()
            get("location") == "${s.capitalize()} line: $l".toString()
        }
    }

    def "can deal with LAE when everything is null"() {
        given:
        def c = new RuntimeException("!")
        def e = new LocationAwareException(c, null as String, null)

        expect:
        with(ExceptionMetadataHelper.getMetadata(e)) {
            get("sourceDisplayName") == null
            get("lineNumber") == null
            get("location") == null
        }
    }

    def "extracts MultiCauseException status"() {
        given:
        def c = new DefaultMultiCauseException("", new RuntimeException("cause 1"), new RuntimeException("cause 2"))

        expect:
        with(ExceptionMetadataHelper.getMetadata(c)) {
            get("isMultiCause") == true.toString()
        }
    }

    def "captures location information from TaskExecutionException"() {
        given:
        def path = ":build:the:path"
        def task = Mock(TaskInternal)
        _ * task.getIdentityPath() >> Path.path(path)
        def te = new TaskExecutionException(task, new RuntimeException("badness"))

        expect:
        with(ExceptionMetadataHelper.getMetadata(te)) {
            get("isMultiCause") == true.toString()
            get("taskPath") == ":build:the:path"
        }
    }

    def "captures location information from ScriptCompilationException"() {
        given:
        int lineNumber = 57
        def file = new File("build.gradle")
        def ex = new ScriptCompilationException(
            "compilation error",
            new IllegalArgumentException("insufficient emoji"),
            new TextResourceScriptSource(new UriTextResource("description", file, Mock(RelativeFilePathResolver))),
            lineNumber
        )

        expect:
        with(ExceptionMetadataHelper.getMetadata(ex)) {
            get("scriptFile") == file.absolutePath
            get("scriptLineNumber") == "$lineNumber"
        }
    }

}
