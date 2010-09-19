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
package org.gradle.logging.internal;

import org.gradle.api.logging.StandardOutputListener;
import org.gradle.logging.StyledTextOutput;

public abstract class AbstractStyledTextOutput implements StyledTextOutput, StandardOutputListener {
    private static final String EOL = System.getProperty("line.separator");

    public StyledTextOutput append(char c) {
        text(String.valueOf(c));
        return this;
    }

    public StyledTextOutput append(CharSequence csq) {
        text(csq == null ? "null" : csq);
        return this;
    }

    public StyledTextOutput append(CharSequence csq, int start, int end) {
        text(csq == null ? "null" : csq.subSequence(start, end));
        return this;
    }

    public StyledTextOutput format(String pattern, Object... args) {
        text(String.format(pattern, args));
        return this;
    }

    public StyledTextOutput println(Object text) {
        text(text);
        println();
        return this;
    }

    public StyledTextOutput formatln(String pattern, Object... args) {
        format(pattern, args);
        println();
        return this;
    }

    public void onOutput(CharSequence output) {
        text(output);
    }

    public StyledTextOutput println() {
        text(EOL);
        return this;
    }

    public StyledTextOutput text(Object text) {
        doAppend(text == null ? "null" : text.toString());
        return this;
    }

    protected abstract void doAppend(String text);

    public StyledTextOutput style(Style style) {
        return this;
    }
}
