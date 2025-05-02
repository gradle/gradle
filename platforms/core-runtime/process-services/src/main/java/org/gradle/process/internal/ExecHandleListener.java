/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.process.ExecResult;

public interface ExecHandleListener {
    /**
     * Called before the execution of the ExecHandle starts. Unlike {@link #executionStarted(ExecHandle)}, this method is called synchronously from {@link ExecHandle#start()}.
     *
     * @param execHandle the handle that is about to start
     */
    void beforeExecutionStarted(ExecHandle execHandle);

    /**
     * Called before the worker thread starts running the {@code execHandle}.
     *
     * @param execHandle the handle that is about to start
     */
    void executionStarted(ExecHandle execHandle);

    void executionFinished(ExecHandle execHandle, ExecResult execResult);
}
