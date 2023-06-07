/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.problems;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * <p>This interface represents the model of a problem which needs to be reported to a user.
 * A problem must:</p>
 *
 * <ul>
 *     <li>be associated with a {@link #getSeverity() severity} (e.g, critical, warning, deprecation, ...)</li>
 *     <li>have a unique {@link #getId() ID} </li>
 *     <li>have a {@link #getWhere() location}, represented by its "context"</li>
 *     <li>have a {@link #getWhat() short description} ("what happened")</li>
 * </ul>
 *
 * <p>In addition, it is strongly recommended that problems carry:</p>
 *
 * <ul>
 *     <li>a description of {@link #getWhy() why} the problem happened</li>
 *     <li>a {@link #getLongDescription() longer description} of the problem</li>
 *     <li>a link to {@link #getDocumentationLink() documentation} providing throughful explanation of the problem</li>
 *     <li>a list of {@link #getPossibleSolutions() possible solutions to the problem}</li>
 * </ul>
 *
 * <p>Each of the solutions, themselves, provide a short and a long description, as well as
 * an optional documentation link.</p>
 *
 * <p>It is expected that different problem categories are represented by different concrete
 * instantiations of this interface, where the generic type representation go away.</p>
 *
 * <p>The problem ID doesn't have to be displayed to the user, but it can be useful. There
 * are often problems which are the same but are expressed differently depending on the
 * context. Therefore, assigning a unique id is a good way to uniquely identify a problem.
 * It can also be useful to put in place mechanisms to silence particular problems (for
 * example, suppress warnings for a specific problem).</p>
 *
 * <p>The combination of (id, context) can also be used to pinpoint a particular problem.</p>
 *
 * <p>As many different projects have a different opinion on what severity levels exist,
 * or what kind of problems we can find, the severity is a generic type argument which
 * should be specified by your application.</p>
 *
 * @param <ID> an ID enumeration. Each problem should be uniquely identified by ID.
 * @param <SEVERITY> the severity type
 * @param <CONTEXT> the type of the context, representing where the problem happened
 */
public interface Problem<ID extends Enum<ID>, SEVERITY extends Enum<SEVERITY>, CONTEXT> extends WithId<ID>, WithDescription, WithDocumentationLink {
    /**
     * @return How severe is the problem
     */
    SEVERITY getSeverity();

    /**
     * Where did the problem surface?
     * @return can refer to a source file, a plugin, or any place which lets the user
     * understand where a problem happened
     */
    CONTEXT getWhere();

    /**
     * Should return a short, human-understandable description of the problem.
     * By default, this returns the short description.
     * @return what problem happened
     */
    default String getWhat() {
        return getShortDescription();
    }

    /**
     * If possible, a problem should tell why it happened
     * @return a description of why the problem happened
     */
    default Optional<String> getWhy() {
        return Optional.empty();
    }

    /**
     * A list of solutions, or potential solutions to the reported problem.
     * @return a list of solutions. Shouldn't be null.
     */
    default List<Solution> getPossibleSolutions() {
        return Collections.emptyList();
    }

    /**
     * If this problem is a consequence of another problem, return the
     * originating problem.
     * @return a problem which caused the current one to show up, if any.
     */
    default Optional<Problem<?, ?, ?>> getCause() {
        return Optional.empty();
    }
}
