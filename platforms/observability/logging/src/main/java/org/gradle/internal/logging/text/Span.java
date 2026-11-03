/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.text;

import com.google.common.base.Objects;

import java.io.Serializable;

public class Span implements Serializable {
    private final Style style;
    private final String text;

    public Span(Style style, String text) {
        this.style = style;
        this.text = text;
    }

    public Span(String text) {
        this(Style.NORMAL, text);
    }

    public Style getStyle() {
        return style;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        Span rhs = (Span) obj;
        return Objects.equal(getStyle(), rhs.getStyle())
            && Objects.equal(getText(), rhs.getText());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getStyle(), getText());
    }
}
