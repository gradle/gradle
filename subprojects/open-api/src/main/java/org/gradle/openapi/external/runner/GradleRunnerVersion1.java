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
package org.gradle.openapi.external.runner;

/**
 * This executes gradle commands in an external process.
 * @deprecated Use the tooling API instead
 */
@Deprecated
public interface GradleRunnerVersion1 {
    /**
     * Call this to execute the specified command line.
     *
     * @param commandLine the command to execute
     */
    public void executeCommand(String commandLine);

    /**
     * Call this to stop the gradle command. This is killing the process, not gracefully exiting.
     */
    public void killProcess();
}
