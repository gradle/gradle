/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.Action;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.AdditionalDataSpec;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;

import javax.annotation.Nullable;

public interface InternalProblemSpec extends ProblemSpec {

    /**
     * Attaches additional data describing the problem.
     * <p>
     * Only the types listed for {@link AdditionalData} can be used as arguments, otherwise an invalid problem report will be created.
     * <p>
     * If not additional data was configured for this problem, then a new instance will be created. If additional data was already configured, then the existing instance will be used and the configuration will be applied to it.
     *
     * @param specType the type of the additional data configurer (see the AdditionalDataSpec interface for the list of supported types)
     * @param config  The action configuring the additional data
     * @return this
     * @param <U> The type of the configurator object that will be applied to the additional data
     */
    <U extends AdditionalDataSpec> InternalProblemSpec additionalData(Class<? extends U> specType, Action<? super U> config);

    /**
     * Declares that this problem was emitted by a task with the given path.
     *
     * @param buildTreePath the absolute path of the task within the build tree
     * @return this
     */
    InternalProblemSpec taskPathLocation(String buildTreePath);

    /**
     * Declares the documentation for this problem.
     *
     * @return this
     */
    InternalProblemSpec documentedAt(@Nullable DocLink doc);

    @Override
    InternalProblemSpec id(String name, String displayName);

    @Override
    InternalProblemSpec id(String name, String displayName, ProblemGroup parent);

    @Override
    InternalProblemSpec contextualLabel(String contextualLabel);

    @Override
    InternalProblemSpec documentedAt(String url);

    @Override
    InternalProblemSpec fileLocation(String path);

    @Override
    InternalProblemSpec lineInFileLocation(String path, int line);

    @Override
    InternalProblemSpec lineInFileLocation(String path, int line, int column);

    @Override
    InternalProblemSpec lineInFileLocation(String path, int line, int column, int length);

    @Override
    InternalProblemSpec offsetInFileLocation(String path, int offset, int length);

    @Override
    InternalProblemSpec stackLocation();

    @Override
    InternalProblemSpec details(String details);

    @Override
    InternalProblemSpec solution(String solution);

    @Override
    InternalProblemSpec withException(Throwable t);

    @Override
    InternalProblemSpec severity(Severity severity);
}
