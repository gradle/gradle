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
import org.gradle.api.problems.AdditionalDataSpec;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.Severity;

public interface InternalProblemBuilder extends InternalProblemSpec {

    /**
     * Creates the new problem. Calling this method won't report the problem via build operations, it can be done separately by calling {@link org.gradle.api.problems.internal.InternalProblemReporter#report(Problem)}.
     *
     * @return the new problem
     */
    Problem build();

    @Override
    InternalProblemBuilder id(String name, String displayName);

    @Override
    InternalProblemBuilder id(String name, String displayName, ProblemGroup parent);

    @Override
    InternalProblemBuilder taskPathLocation(String buildTreePath);

    @Override
    InternalProblemBuilder documentedAt(DocLink doc);

    @Override
    InternalProblemBuilder contextualLabel(String contextualLabel);

    @Override
    InternalProblemBuilder documentedAt(String url);

    @Override
    InternalProblemBuilder fileLocation(String path);

    @Override
    InternalProblemBuilder lineInFileLocation(String path, int line);

    @Override
    InternalProblemBuilder lineInFileLocation(String path, int line, int column, int length);

    @Override
    InternalProblemBuilder offsetInFileLocation(String path, int offset, int length);

    @Override
    InternalProblemBuilder stackLocation();

    @Override
    InternalProblemBuilder details(String details);

    @Override
    InternalProblemBuilder solution(String solution);

    @Override
    <U extends AdditionalDataSpec> InternalProblemBuilder additionalData(Class<? extends U> specType, Action<? super U> config);

    @Override
    InternalProblemBuilder withException(Throwable t);

    @Override
    InternalProblemBuilder severity(Severity severity);
}
