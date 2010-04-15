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
package org.gradle.api.logging;

/**
 * @author Hans Dockter
 */
public interface StandardOutputCapture {
    /**
     * Starts output redirection to the Gradle logging system. System.out is redirected to the INFO level. System.err is
     * always redirected to the ERROR level.
     *
     * For more fine-grained control see {@link org.gradle.api.logging.StandardOutputLogging}.
     *
     * @return this
     */
    StandardOutputCapture start();

    /**
     * Restores System.out and System.err to the values they had before {@link #start()} has been called.
     *
     * @return this
     */
    StandardOutputCapture stop();
}
