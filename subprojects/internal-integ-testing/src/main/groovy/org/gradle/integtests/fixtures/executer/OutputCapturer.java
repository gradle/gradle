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
import org.gradle.internal.io.StreamByteBuffer;

import java.io.OutputStream;

class OutputCapturer {
    private final StreamByteBuffer buffer;
    private final OutputStream outputStream;
    private final StringBuilder outputStringBuilder = new StringBuilder();
    private final String outputEncoding;
    private String cachedOutputString;

    public OutputCapturer(OutputStream standardStream, String outputEncoding) {
        this.buffer = new StreamByteBuffer();
        this.outputStream = new CloseShieldOutputStream(new TeeOutputStream(standardStream, buffer.getOutputStream()));
        this.outputEncoding = outputEncoding;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public String getOutputAsString() {
        if (cachedOutputString == null || buffer.totalBytesUnread() > 0) {
            outputStringBuilder.append(buffer.readAsString(outputEncoding));
            cachedOutputString = outputStringBuilder.toString();
        }
        return cachedOutputString;
    }

    public void reset() {
        buffer.clear();
        outputStringBuilder.setLength(0);
        cachedOutputString = null;
    }
}
