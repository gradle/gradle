/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.operations.problems;

/**
 * A file location.
 *
 * @since 8.14
 */
public interface FileLocation extends ProblemLocation {

    // TODO: Add getFileType() - build logic file, software definition, application code

    /**
     * The absolute path of the file.
     *
     * @since 8.14
     */
    String getPath();

}
