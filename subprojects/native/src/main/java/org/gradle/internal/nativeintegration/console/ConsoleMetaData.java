/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeintegration.console;

public interface ConsoleMetaData {
    /**
     * Returns true if the current process' stdout is attached to the console.
     */
    boolean isStdOut();

    /**
     * Returns true if the current process' stderr is attached to the console.
     */
    boolean isStdErr();

    /**
     * <p>Returns the number of columns available in the console.</p>
     *
     * @return The number of columns available in the console. If no information is available return 0.
     */
    int getCols();
}
