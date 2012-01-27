/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.logging.StyledTextOutput;

/**
 * A {@link StyledTextOutput} which prefixes each line of text with some fixed prefix. Does not prefix the first line.
 */
public class LinePrefixingStyledTextOutput extends AbstractLineChoppingStyledTextOutput {
    private final StyledTextOutput output;
    private final CharSequence prefix;

    public LinePrefixingStyledTextOutput(StyledTextOutput output, CharSequence prefix) {
        this.output = output;
        this.prefix = prefix;
    }

    @Override
    protected void doLineText(CharSequence text, boolean terminatesLine) {
        output.text(text);
    }

    @Override
    protected void doStartLine() {
        output.text(prefix);
    }

    @Override
    protected void doStyleChange(Style style) {
        output.style(style);
    }
}
