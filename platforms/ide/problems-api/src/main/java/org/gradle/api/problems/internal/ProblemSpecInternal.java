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
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;
import org.gradle.problems.ProblemDiagnostics;
import org.jspecify.annotations.Nullable;

public interface ProblemSpecInternal extends ProblemSpec {

    /**
     * Attaches additional data describing the problem.
     * <p>
     * Only the types listed for {@link org.gradle.api.problems.AdditionalData} can be used as arguments, otherwise an invalid problem report will be created.
     * <p>
     * If not additional data was configured for this problem, then a new instance will be created. If additional data was already configured, then the existing instance will be used and the configuration will be applied to it.
     *
     * @param specType the type of the additional data configurer (see the AdditionalDataSpec interface for the list of supported types)
     * @param config  The action configuring the additional data
     * @return this
     * @param <U> The type of the configurator object that will be applied to the additional data
     */
    <U extends org.gradle.api.problems.internal.AdditionalDataSpec> ProblemSpecInternal additionalDataInternal(Class<? extends U> specType, Action<? super U> config);

    @Override
    <T extends AdditionalData> ProblemSpecInternal additionalData(Class<T> type, Action<? super T> config);

    /**
     * Declares that this problem was emitted by a task with the given path.
     *
     * @param buildTreePath the absolute path of the task within the build tree
     * @return this
     */
    ProblemSpecInternal taskLocation(String buildTreePath);

    /**
     * Declares the documentation for this problem.
     *
     * @return this
     */
    ProblemSpecInternal documentedAt(@Nullable DocLink doc);

    /**
     * Defines the context-independent identifier for this problem.
     * <p>
     * It is a mandatory property to configure when emitting a problem with {@link ProblemReporter}.
     * ProblemId instances can be created via {@link ProblemId#create(String, String, ProblemGroup)}.
     *
     * @param problemId the problem id
     * @return this
     */
    ProblemSpecInternal id(ProblemId problemId);

    /**
     * Defines simple identification for this problem.
     * <p>
     * It is a mandatory property to configure when emitting a problem with {@link ProblemReporter}.
     *
     * @param name the name of the problem. As a convention kebab-case-formatting should be used.
     * @param displayName a human-readable representation of the problem, free of any contextual information.
     * @param parent the container problem group.
     * @return this
     * @since 8.8
     */
    ProblemSpecInternal id(String name, String displayName, ProblemGroup parent);

    @Override
    ProblemSpecInternal contextualLabel(String contextualLabel);

    @Override
    ProblemSpecInternal documentedAt(String url);

    @Override
    ProblemSpecInternal fileLocation(String path);

    @Override
    ProblemSpecInternal lineInFileLocation(String path, int line);

    @Override
    ProblemSpecInternal lineInFileLocation(String path, int line, int column);

    @Override
    ProblemSpecInternal lineInFileLocation(String path, int line, int column, int length);

    @Override
    ProblemSpecInternal offsetInFileLocation(String path, int offset, int length);

    @Override
    ProblemSpecInternal stackLocation();

    @Override
    ProblemSpecInternal details(String details);

    @Override
    ProblemSpecInternal solution(String solution);

    @Override
    ProblemSpecInternal withException(Throwable t);

    @Override
    ProblemSpecInternal severity(Severity severity);

    /**
     * The diagnostics to determine the stacktrace and the location of the problem.
     * <p>
     * We pass this in when we already have a diagnostics object, for example for deprecation warnings.
     */
    ProblemSpecInternal diagnostics(ProblemDiagnostics diagnostics);
}
