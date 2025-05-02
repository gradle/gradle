/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner;

import org.gradle.tooling.UnsupportedVersionException;

/**
 * Thrown when a build cannot be executed due to the runner being in an invalid state.
 *
 * @since 2.6
 * @see GradleRunner#build()
 * @see GradleRunner#buildAndFail()
 */
public class InvalidRunnerConfigurationException extends IllegalStateException {
    public InvalidRunnerConfigurationException(String s) {
        super(s);
    }

    public InvalidRunnerConfigurationException(String s, UnsupportedVersionException e) {
        super(s, e);
    }
}
