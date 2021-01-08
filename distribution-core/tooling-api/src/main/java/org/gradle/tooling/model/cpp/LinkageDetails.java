/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.model.cpp;

import org.gradle.tooling.model.Task;

import java.io.File;
import java.util.List;

/**
 * Represents the linkage details for a binary.
 *
 * @since 4.10
 */
public interface LinkageDetails {
    /**
     * Returns details of the link task for the binary. This is the task that should be run to produce the binary output, but may not necessarily be the task that links the binary. For example, the task may do some post processing of the binary.
     */
    Task getLinkTask();

    /**
     * Returns the output location of this binary.
     */
    File getOutputLocation();

    /**
     * Returns any additional linker arguments.
     */
    List<String> getAdditionalArgs();
}
