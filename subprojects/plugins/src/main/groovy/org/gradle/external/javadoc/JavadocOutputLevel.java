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

package org.gradle.external.javadoc;

/**
 * This enum maps to the -verbose and -quiet options of the javadoc executable.
 */
public enum JavadocOutputLevel {
    /**
     * -verbose
     * 
     * Provides more detailed messages while javadoc is running. Without the verbose option,
     * messages appear for loading the source files, generating the documentation (one message per source file), and sorting.
     * The verbose option causes the printing of additional messages specifying the number of milliseconds to parse each java source file.
     */
    VERBOSE,
    /**
     * -quiet
     *
     * Shuts off non-error and non-warning messages, leaving only the warnings and errors appear,
     * making them easier to view. Also suppresses the version string. 
     */
    QUIET
}
