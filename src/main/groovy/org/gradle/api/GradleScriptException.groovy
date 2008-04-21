/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api

/**
 * @author Hans Dockter
 */
class GradleScriptException extends GradleException {
    String scriptName
    List lineNumbers
    int lineNumberOffset

    GradleScriptException(Throwable cause, String scriptName, int lineNumberOffset = 0) {
        super(cause)
        this.lineNumberOffset = lineNumberOffset
        this.scriptName = scriptName
        lineNumbers = cause.stackTrace.findAll {it.fileName == scriptName && it.lineNumber >= 0}.collect {it.lineNumber}
    }

    String getMessage() {
        String lineInfo = lineNumbers ? "in line(s): ${lineNumbersWithOffset.join(' ')}" : 'No line info available from stacktrace.'
        super.getMessage() + " Buildscript=$scriptName $lineInfo"
    }

    List getLineNumbersWithOffset() {
        lineNumbers.collect {((int) it) - lineNumberOffset}
    }

}
