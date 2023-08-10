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

package org.gradle.api.problems.interfaces;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Problem description.
 *
 * @since 8.4
 */
@Incubating
public interface Problem {


    ProblemGroup getProblemGroup();

    String getMessage();

    Severity getSeverity();

    @Nullable
    ProblemLocation getWhere();

    @Nullable
    DocLink getDocumentationLink();

    @Nullable
    String getDescription();

    List<String> getSolutions();

    @Nullable
    Throwable getCause();

    /**
     * Mandotory field for problem type.
     */
    String getProblemType();

    Map<String, String> getAdditionalMetadata();
}
