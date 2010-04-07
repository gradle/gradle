/*
 * Copyright 2010 the original author or authors.
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
    private final PrintStream outStream;
    private final LogLevel outLevel;
    private final PrintStream errStream;
    private final LogLevel errLevel;

    public StandardOutputState(PrintStream outStream, LogLevel outLevel, PrintStream errStream, LogLevel errLevel) {
        this.outStream = outStream;
        this.outLevel = outLevel;
        this.errStream = errStream;
        this.errLevel = errLevel;
    }

    public PrintStream getOutStream() {
        return outStream;
    }

    public PrintStream getErrStream() {
        return errStream;
    }

    public LogLevel getOutLevel() {
        return outLevel;
    }

    public LogLevel getErrLevel() {
        return errLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }

        StandardOutputState other = (StandardOutputState) o;
        if (!outStream.equals(other.outStream)) {
            return false;
        }
        if (!errStream.equals(other.errStream)) {
            return false;
        }
        if (outLevel != other.outLevel) {
            return false;
        }
        if (errLevel != other.errLevel) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return outStream.hashCode() ^ errStream.hashCode();
    }
}
