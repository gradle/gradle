/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.functional.internal;

import org.gradle.api.UncheckedIOException;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class GradleExecutionResult {
    private final ByteArrayOutputStream standardOutput;
    private final ByteArrayOutputStream standardError;
    private final List<String> executedTasks = new ArrayList<String>();
    private final List<String> skippedTasks = new ArrayList<String>();
    private Throwable throwable;

    public GradleExecutionResult(ByteArrayOutputStream standardOutput, ByteArrayOutputStream standardError) {
        this.standardOutput = standardOutput;
        this.standardError = standardError;
    }

    public String getStandardOutput() {
        return getUTF8EncodedString(standardOutput);
    }

    public String getStandardError() {
        return getUTF8EncodedString(standardError);
    }

    private String getUTF8EncodedString(ByteArrayOutputStream outputStream) {
        try {
            return outputStream.toString("UTF-8");
        } catch(UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> getExecutedTasks() {
        return executedTasks;
    }

    public List<String> getSkippedTasks() {
        return skippedTasks;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public boolean isSuccessful() {
        return throwable == null;
    }
}
