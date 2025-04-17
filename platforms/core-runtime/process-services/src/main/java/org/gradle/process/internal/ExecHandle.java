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
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

// TODO Rename `org.gradle.process.internal.ExecHandle` to `ExecHandleInternal`,
//      to avoid confusing with the new public `org.gradle.process.ExecHandle`.
//      I haven't done it yet because I wanted to keep the PR focused and not litter it with a tonne of renames.
public interface ExecHandle extends org.gradle.process.ExecHandle {

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
     * Returns the result of the process execution, if it has finished.  If the process has not finished, returns null.
     */
    @Nullable
    ExecResult getExecResult();

    void addListener(ExecHandleListener listener);

    void removeListener(ExecHandleListener listener);
}
