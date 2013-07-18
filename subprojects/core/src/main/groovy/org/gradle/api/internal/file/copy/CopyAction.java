/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.tasks.WorkResult;

/**
 * An object that processes a stream of {@link FileCopyDetailsInternal}
 */
public interface CopyAction {

    /**
     * Processes a stream of FileCopyDetailsInternal, represented as an action.
     * <p>
     * The stream action takes an action as it argument that is called with each file to copy.
     */
    public WorkResult execute(Action<Action<? super FileCopyDetailsInternal>> stream);

}
