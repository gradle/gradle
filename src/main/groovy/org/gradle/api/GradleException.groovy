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

package org.gradle.api;

/**
 * @author Hans Dockter
 */
public class GradleException extends RuntimeException {
    String scriptName

    public GradleException() {
    }

    public GradleException(String message) {
        super(message);
    }

    public GradleException(String message, Throwable cause) {
        super(message, cause);
    }

    public GradleException(Throwable cause) {
        super(cause);
    }

    public GradleException(Throwable cause, String scriptName) {
        super(cause)
        this.scriptName = scriptName
    }

    String getMessage() {
        if (!scriptName) {
            return super.getMessage();
        }
        List lineNumbers = []
        Throwable currentException = this
        while (currentException != null) {
            lineNumbers.addAll(currentException.stackTrace.findAll {it.fileName.equals(scriptName) && it.lineNumber >= 0}.collect {it.lineNumber})
            currentException = currentException.cause
        }
        String lineInfo = lineNumbers ? "in line(s): ${lineNumbers.join(' ')}" : 'No line info available from stacktrace.'
        super.getMessage() + " Buildscript=$scriptName $lineInfo"
    }
}