/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.process.JavaForkOptions;

public interface JavaForkOptionsInternal extends JavaForkOptions {

    /**
     * Returns true if the given options are compatible with this set of options.
     */
    boolean isCompatibleWith(JavaForkOptions options);

    /**
     * Sets extra JVM arguments to a Java process without checking debug configuration.
     */
    void setExtraJvmArgs(Iterable<?> jvmArgs);


    /**
     * Checks supplied JVM arguments with purpose to ignore debug configuration in favor of the supplied arguments.
     */
    void checkDebugConfiguration(Iterable<?> arguments);
}
