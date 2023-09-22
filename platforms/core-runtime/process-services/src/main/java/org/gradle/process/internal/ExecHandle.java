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

import java.io.File;
import java.util.List;
import java.util.Map;

public interface ExecHandle {

    File getDirectory();

    String getCommand();

    List<String> getArguments();

    Map<String, String> getEnvironment();

    /**
     * Starts this process, blocking until the process has started.
     *
     * @return this
     */
    ExecHandle start();

    void removeStartupContext();

    ExecHandleState getState();

    /**
     * Aborts the process, blocking until the process has exited. Does nothing if the process has already completed.
     */
    void abort();

    /**
     * Waits for the process to finish. Returns immediately if the process has already completed.
     *
     * @return result
     */
    ExecResult waitForFinish();

    void addListener(ExecHandleListener listener);

    void removeListener(ExecHandleListener listener);
}
