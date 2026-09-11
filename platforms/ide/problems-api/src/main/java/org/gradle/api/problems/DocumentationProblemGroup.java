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
 * The predefined {@code Documentation} root problem group.
 * <p>
 * Production of documentation of any form, including API docs, manuals, or websites.
 * <p>
 * The predefined sub-groups are available as properties. Further sub-groups can be created with {@link #group(String)}.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class DocumentationProblemGroup extends RootProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected DocumentationProblemGroup() {
    }

    /**
     * The {@code Javadoc} sub-group.
     * <p>
     * Configuration and generation of Javadoc API documentation, including tag validation, doclets, or reference
     * resolution.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getJavadoc();

    /**
     * The {@code Groovydoc} sub-group.
     * <p>
     * Configuration and generation of Groovy API documentation, including tag validation, doclets, or reference
     * resolution.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getGroovydoc();

    /**
     * The {@code Scaladoc} sub-group.
     * <p>
     * Configuration and generation of Scala API documentation, including tag validation, doclets, or reference
     * resolution.
     *
     * @since 9.9.0
     */
    public abstract SubProblemGroup getScaladoc();
}
