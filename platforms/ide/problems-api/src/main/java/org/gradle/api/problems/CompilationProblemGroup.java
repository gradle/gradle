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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * The predefined {@code Compilation} root problem group.
 * <p>
 * Code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class CompilationProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected CompilationProblemGroup() {
    }

    /**
     * The {@code C++} sub-group.
     * <p>
     * C++ code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getCpp();

    /**
     * The {@code Groovy} sub-group.
     * <p>
     * Groovy code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getGroovy();

    /**
     * The {@code Java} sub-group.
     * <p>
     * Java code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getJava();

    /**
     * The {@code Kotlin} sub-group.
     * <p>
     * Kotlin code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getKotlin();

    /**
     * The {@code Scala} sub-group.
     * <p>
     * Scala code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getScala();

    /**
     * The {@code Swift} sub-group.
     * <p>
     * Swift code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getSwift();
}
