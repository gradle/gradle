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
package org.gradle.foundation.ipc.basic;

import java.io.File;
import java.util.HashMap;

/*
   Fill this in with whatever you need to launch your external process.
*/
public interface ExecutionInfo {
    /**
     * @return the command line arguments passed to gradle.
     */
    public String[] getCommandLineArguments();

    /**
     * @return the working directory from which the JVM will execute.
     */
    public File getWorkingDirectory();

    /**
     * @return a hashmap of environment variables and their values that are passed to the JVM that is created.
     */
    public HashMap<String, String> getEnvironmentVariables();

    /**
     * Notification that execution has completed. This provides a place to cleanup any init scripts you have created.
     */
    public void processExecutionComplete();
}
