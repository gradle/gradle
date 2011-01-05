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
package org.gradle.integtests.fixtures;

import org.gradle.util.Jvm;
import org.gradle.util.TestFile;

public interface BasicGradleDistribution {
    /**
     * Returns the root directory of the installed distribution
     */
    TestFile getGradleHomeDir();

    /**
     * Returns the binary distribution.
     */
    TestFile getBinDistribution();

    /**
     * Returns the version of this distribution.
     */
    String getVersion();

    /**
     * Creates an executer which will use this distribution.
     */
    GradleExecuter executer();

    /**
     * Returns true if this distribution supports the given JVM.
     */
    boolean worksWith(Jvm jvm);
}
