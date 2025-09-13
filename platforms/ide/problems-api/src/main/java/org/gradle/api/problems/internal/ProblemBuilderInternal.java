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
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;

public interface ProblemBuilderInternal extends ProblemSpecInternal {

    /**
     * Creates the new problem. Calling this method won't report the problem via build operations, it can be done separately by calling {@link ProblemReporterInternal#report(Problem)}.
     *
     * @return the new problem
     */
    ProblemInternal build();

    @Override
    ProblemBuilderInternal id(ProblemId problemId);

    @Override
    ProblemBuilderInternal id(String name, String displayName, ProblemGroup parent);

    @Override
    ProblemBuilderInternal taskLocation(String buildTreePath);

    @Override
    ProblemBuilderInternal documentedAt(DocLink doc);

    @Override
    ProblemBuilderInternal contextualLabel(String contextualLabel);

    @Override
    ProblemBuilderInternal documentedAt(String url);

    @Override
    ProblemBuilderInternal fileLocation(String path);

    @Override
    ProblemBuilderInternal lineInFileLocation(String path, int line);

    @Override
    ProblemBuilderInternal lineInFileLocation(String path, int line, int column, int length);

    @Override
    ProblemBuilderInternal offsetInFileLocation(String path, int offset, int length);

    @Override
    ProblemBuilderInternal stackLocation();

    @Override
    ProblemBuilderInternal details(String details);

    @Override
    ProblemBuilderInternal solution(String solution);

    @Override
    <U extends AdditionalDataSpec> ProblemBuilderInternal additionalDataInternal(Class<? extends U> specType, Action<? super U> config);

    // interface should be public <T> void additionalData(Class<T> type, Action<? super T> config)
    @Override
    <T extends AdditionalData> ProblemBuilderInternal additionalData(Class<T> type, Action<? super T> config);

    @Override
    ProblemBuilderInternal withException(Throwable t);

    @Override
    ProblemBuilderInternal severity(Severity severity);

    ProblemsInfrastructure getInfrastructure();
}
