/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.logging.compatbridge;

import org.gradle.logging.StyledTextOutput;

public class DeprecatedStyledTextOutput implements StyledTextOutput {
    private final org.gradle.internal.logging.text.StyledTextOutput delegate;

    public DeprecatedStyledTextOutput(org.gradle.internal.logging.text.StyledTextOutput delegate) {
        this.delegate = delegate;
    }

    private StyledTextOutput wrap(org.gradle.internal.logging.text.StyledTextOutput append) {
        return new DeprecatedStyledTextOutput(append);
    }

    @Override
    public StyledTextOutput append(char c) {
        return wrap(delegate.append(c));
    }

    @Override
    public StyledTextOutput println(Object text) {
        return wrap(delegate.println(text));
    }

    @Override
    public StyledTextOutput exception(Throwable throwable) {
        return wrap(delegate.exception(throwable));
    }

    @Override
    public StyledTextOutput append(CharSequence csq, int start, int end) {
        return wrap(delegate.append(csq, start, end));
    }

    public StyledTextOutput withStyle(StyledTextOutput.Style style) {
        return wrap(delegate.withStyle(mapStyle(style)));
    }

    public StyledTextOutput style(StyledTextOutput.Style style) {
        return wrap(delegate.style(mapStyle(style)));
    }

    private org.gradle.internal.logging.text.StyledTextOutput.Style mapStyle(StyledTextOutput.Style style) {
        return org.gradle.internal.logging.text.StyledTextOutput.Style.valueOf(style.name());
    }

    @Override
    public StyledTextOutput println() {
        return wrap(delegate.println());
    }

    @Override
    public StyledTextOutput append(CharSequence csq) {
        return wrap(delegate.append(csq));
    }

    @Override
    public StyledTextOutput text(Object text) {
        return wrap(delegate.text(text));
    }

    @Override
    public StyledTextOutput formatln(String pattern, Object... args) {
        return wrap(delegate.formatln(pattern, args));
    }

    @Override
    public StyledTextOutput format(String pattern, Object... args) {
        return wrap(delegate.format(pattern, args));
    }
}
