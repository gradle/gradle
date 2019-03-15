/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

/**
 * Represents the complete changes in execution state
 */
public interface ExecutionStateChanges {

    /**
     * Returns all change messages for inputs and outputs.
     */
    ImmutableList<String> getAllChangeMessages();

    /**
     * Creates the input changes for the given.
     *
     * @param incrementalParameterNameByValue Mapping from the actual value of to the parameter name.
     */
    InputChangesInternal createInputChanges(ImmutableMultimap<Object, String> incrementalParameterNameByValue);

    /**
     * Turn these changes into ones forcing a rebuild with the given reason.
     */
    ExecutionStateChanges withEnforcedRebuild(String rebuildReason);
}
