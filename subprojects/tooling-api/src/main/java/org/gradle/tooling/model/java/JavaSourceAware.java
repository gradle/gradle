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

package org.gradle.tooling.model.java;

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.tooling.model.UnsupportedMethodException;

/**
 * An element which might have associated Java sources.
 *
 * @since 2.10
 */
@Incubating
public interface JavaSourceAware {

    /**
     * Returns the settings for Java sources or {@code null} if not a Java element.
     *
     * @return The source settings.
     * @throws UnsupportedMethodException For Gradle versions older than 2.10, where this method is not supported.
     */
    @Nullable
    JavaSourceSettings getJavaSourceSettings() throws UnsupportedMethodException;
}
