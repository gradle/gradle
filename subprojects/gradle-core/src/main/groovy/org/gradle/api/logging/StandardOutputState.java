/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.logging;

import java.io.PrintStream;

/**
 * @author Hans Dockter
 */
public class StandardOutputState {
    private PrintStream outStream;
    private PrintStream errStream;

    public StandardOutputState(PrintStream outStream, PrintStream errStream) {
        this.outStream = outStream;
        this.errStream = errStream;
    }

    public PrintStream getOutStream() {
        return outStream;
    }

    public PrintStream getErrStream() {
        return errStream;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StandardOutputState that = (StandardOutputState) o;

        if (errStream != null ? !errStream.equals(that.errStream) : that.errStream != null) {
            return false;
        }
        if (outStream != null ? !outStream.equals(that.outStream) : that.outStream != null) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int result;
        result = (outStream != null ? outStream.hashCode() : 0);
        result = 31 * result + (errStream != null ? errStream.hashCode() : 0);
        return result;
    }
}
