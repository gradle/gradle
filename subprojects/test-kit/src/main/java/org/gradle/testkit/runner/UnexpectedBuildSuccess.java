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

import org.gradle.api.Incubating;

/**
 * Thrown when executing a build that was expected to fail, but succeeded.
 *
 * @since 2.6
 * @see GradleRunner#buildAndFail()
 */
@Incubating
public class UnexpectedBuildSuccess extends UnexpectedBuildException {
    public UnexpectedBuildSuccess(String message, BuildResult buildResult) {
        super(message, buildResult);
    }
}
