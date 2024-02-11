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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Style {
    public static final Style NORMAL = Style.of(Color.DEFAULT);
    public enum Emphasis {
        BOLD, REVERSE, ITALIC
    }

    public enum Color {
        DEFAULT, YELLOW, RED, GREY, GREEN, BLACK
    }

    public final Set<Emphasis> emphasises;
    public final Color color;

    public Style(Set<Emphasis> emphasises, Color color) {
        this.emphasises = emphasises;
        this.color = color;
    }

    public Set<Emphasis> getEmphasises() {
        return emphasises;
    }

    public Color getColor() {
        return color;
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

        Style rhs = (Style) obj;
        return Objects.equal(getEmphasises(), rhs.getEmphasises())
            && Objects.equal(getColor(), rhs.getColor());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getEmphasises(), getColor());
    }

    public static Style of(Emphasis emphasis) {
        return of(emphasis, Color.DEFAULT);
    }

    public static Style of(Emphasis emphasis, Color color) {
        return new Style(EnumSet.of(emphasis), color);
    }

    public static Style of(Color color) {
        return new Style(Collections.<Emphasis>emptySet(), color);
    }
}
