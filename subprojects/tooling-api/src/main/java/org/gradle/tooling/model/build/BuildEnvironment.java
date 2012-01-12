/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model.build;

import org.gradle.tooling.model.Element;
import org.gradle.tooling.model.UnsupportedMethodException;

/**
 * Informs about the build environment, like Gradle version or the java home in use.
 * <p>
 * Marked as deprecated because the API is not yet confirmed.
 * E.g. we will provide this information for sure, we just haven't yet confirmed the API.
 * <p>
 * @since 1.0-milestone-8
 */
public interface BuildEnvironment extends Element {

    /**
     * Informs about the gradle environment, for example the gradle version.
     */
    GradleEnvironment getGradle();

    /**
     * Informs about the java environment, for example the java home or the jvm args used.
     *
     * @throws org.gradle.tooling.model.UnsupportedMethodException
     * when the gradle version the tooling api is connected to does not support the java environment information.
     */
    JavaEnvironment getJava() throws UnsupportedMethodException;
}
