/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.problems.internal.rendering;

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;

/**
 * Renders problem groups and ids for humans, for example {@code Compilation > Java} and {@code Unused import (in Compilation > Java)}.
 */
public final class ProblemGroupRenderer {

    /**
     * Separates group names when a group chain is rendered for humans, for example {@code Compilation > Java}.
     */
    public static final String SEPARATOR_CHAR = ">";
    public static final String SEPARATOR = " " + SEPARATOR_CHAR + " ";

    private ProblemGroupRenderer() {
    }

    /**
     * Renders the chain of group names from the root for humans, for example {@code Compilation > Java}.
     * <p>
     * This is a rendering, not an identity: identity is structural (see {@code ProblemGroupInternal#structurallyEquals}) and the
     * serialized form is the list of names. A group name that contains the separator or a quote is quoted so the rendering
     * cannot be misread, for example {@code Compilation > "Java > Kotlin"}.
     */
    public static String render(ProblemGroup group) {
        ProblemGroup parent = group.getParent();
        String name = quoteIfNeeded(group.getName());
        return parent == null ? name : render(parent) + SEPARATOR + name;
    }

    /**
     * Renders a problem id for humans as the problem name followed by its group chain, for example
     * {@code Unused import (in Compilation > Java)}.
     */
    public static String render(ProblemId id) {
        return renderProblemName(id.getName()) + " (in " + render(id.getGroup()) + ")";
    }

    /**
     * Renders a problem name for humans. A problem name is a sentence rather than a path segment, and sentences legitimately
     * contain quotes, for example {@code Class "Foo" is not serializable}, so quotes alone do not trigger quoting. A name that
     * contains the separator is quoted, so that it cannot be misread as part of the group chain.
     */
    public static String renderProblemName(String name) {
        return name.contains(SEPARATOR_CHAR) ? quote(name) : name;
    }

    /**
     * Quotes a group name that contains the separator or a quote, escaping quotes and backslashes inside it, so that a
     * rendered chain cannot be misread. Names that need no quoting are returned unchanged.
     */
    public static String quoteIfNeeded(String name) {
        if (!name.contains(SEPARATOR_CHAR) && name.indexOf('"') < 0) {
            return name;
        }
        return quote(name);
    }

    private static String quote(String name) {
        return '"' + name.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
