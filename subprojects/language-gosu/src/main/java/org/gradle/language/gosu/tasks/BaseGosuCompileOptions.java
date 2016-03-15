/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.compile.AbstractOptions;
import org.gradle.api.tasks.gosu.GosuForkOptions;

/**
 * Options for Gosu platform compilation, excluding any options for compilation with Ant.
 */
@Incubating
public class BaseGosuCompileOptions extends AbstractOptions {

    private static final long serialVersionUID = 0;

    private boolean failOnError = true;

    private boolean listFiles;

    private String loggingLevel;

    private GosuForkOptions forkOptions = new GosuForkOptions();

    /**
     * Fail the build on compilation errors.
     */
    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * List files to be compiled.
     */
    public boolean isListFiles() {
        return listFiles;
    }

    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    /**
     * Specifies the amount of logging.
     * Legal values:  none, verbose, debug
     */
    public String getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(String loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    /**
     * Options for running the Gosu compiler in a separate process. These options only take effect
     * if {@code fork} is set to {@code true}.
     */
    public GosuForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(GosuForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }
}
