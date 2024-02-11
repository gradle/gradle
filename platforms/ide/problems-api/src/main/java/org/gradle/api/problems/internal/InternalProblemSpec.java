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

import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;

public interface InternalProblemSpec extends ProblemSpec {

    /**
     * Specifies arbitrary data associated with this problem.
     * <p>
     * The only supported value type is {@link String}. Future Gradle versions may support additional types.
     *
     * @return this
     * @throws RuntimeException for null values and for values with unsupported type.
     */
    InternalProblemSpec additionalData(String key, Object value);

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
    InternalProblemSpec documentedAt(DocLink doc);

    @Override
    InternalProblemSpec label(String label);

    @Override
    InternalProblemSpec category(String category, String... details);

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
    InternalProblemSpec pluginLocation(String pluginId);

    @Override
    InternalProblemSpec stackLocation();

    @Override
    InternalProblemSpec details(String details);

    @Override
    InternalProblemSpec solution(String solution);

    @Override
    InternalProblemSpec withException(RuntimeException e);

    @Override
    InternalProblemSpec severity(Severity severity);
}
