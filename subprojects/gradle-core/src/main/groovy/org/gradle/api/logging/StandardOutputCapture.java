/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.logging;

/**
 * @author Hans Dockter
 */
public interface StandardOutputCapture {
    /**
     * If {@link #isEnabled()} is true, it starts output redirection to the Gradle logging system.
     * System.out is redirected to the INFO level. System.err is always redirected to the
     * ERROR level.
     *
     * If the standard output is captured globally already, setting the task output capturing has no effect.
     *
     * For more fine-grained control see {@link org.gradle.api.logging.StandardOutputLogging}.
     */
    StandardOutputCapture start();

    /**
     * If {@link #isEnabled()} is true, it restores System.out and System.err to the values they had before
     * {@link #start()} has been called.
     */
    StandardOutputCapture stop();

    /**
     * Whether a call to {@link #start()} will trigger redirection of the output.
     */
    boolean isEnabled();

    /**
     * 
     * Returns the log level the output is redirected to.
     */
    LogLevel getLevel();
}
