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

package org.gradle.integtests.fixtures.executer;

import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.internal.UncheckedException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

class OutputCapturer {
    private final ByteArrayOutputStream buffer;
    private final OutputStream outputStream;
    private final String outputEncoding;

    public OutputCapturer(OutputStream standardStream, String outputEncoding) {
        this.buffer = new ByteArrayOutputStream();
        this.outputStream = new CloseShieldOutputStream(new TeeOutputStream(standardStream, buffer));
        this.outputEncoding = outputEncoding;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public String getOutputAsString() {
        try {
            return buffer.toString(outputEncoding);
        } catch (UnsupportedEncodingException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
