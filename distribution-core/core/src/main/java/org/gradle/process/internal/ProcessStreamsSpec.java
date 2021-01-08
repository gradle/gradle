/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.process.internal;

import java.io.InputStream;
import java.io.OutputStream;


public class ProcessStreamsSpec {

    private InputStream standardInput;
    private OutputStream standardOutput;
    private OutputStream errorOutput;

    public ProcessStreamsSpec setStandardInput(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream == null!");
        }
        standardInput = inputStream;
        return this;
    }

    public InputStream getStandardInput() {
        return standardInput;
    }

    public ProcessStreamsSpec setStandardOutput(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream == null!");
        }
        standardOutput = outputStream;
        return this;
    }

    public OutputStream getStandardOutput() {
        return standardOutput;
    }

    public ProcessStreamsSpec setErrorOutput(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream == null!");
        }
        errorOutput = outputStream;
        return this;
    }

    public OutputStream getErrorOutput() {
        return errorOutput;
    }
}
