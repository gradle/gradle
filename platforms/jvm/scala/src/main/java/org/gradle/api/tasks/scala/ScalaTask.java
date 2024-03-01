/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks.scala;

import org.gradle.api.Named;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Internal;

/**
 * Common interface for Scala tasks.
 *
 * @since 8.8
 */
public interface ScalaTask extends Task, IConventionAware, Named {

    /**
     * {@inheritDoc}
     *
     * <p><em>Note:</em> The "{@code extends }{@link Named}" clause was added to this interface as a temporary solution to work around the
     * problem that {@link Task} itself does not currently extend {@link Named}. Once that's fixed, we should remove both the clause as well
     * as this method declaration.</p>
     *
     * @see <a href="https://github.com/gradle/gradle/pull/28282">PR #28282 - Make Task extend Named, just like Configuration</a>
     */
    @Internal
    @Override
    String getName();

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    ConventionMapping getConventionMapping();

    @Classpath
    FileCollection getClasspath();

    void setClasspath(FileCollection classpath);

    @Classpath
    FileCollection getScalaClasspath();

    void setScalaClasspath(FileCollection scalaClasspath);
}
