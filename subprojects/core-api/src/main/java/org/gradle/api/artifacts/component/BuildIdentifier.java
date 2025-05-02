/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.artifacts.component;

/**
 * Identifies a Gradle build. The identifier is unique within a Gradle invocation, so for example, each included build will have a different identifier.
 */
public interface BuildIdentifier {

    /**
     * Absolute build path of the build within the Gradle invocation.
     *
     * @since 8.2
     */
    String getBuildPath();

    /**
     * The name of the build.
     *
     * @deprecated Use {@link #getBuildPath()} instead.
     */
    @Deprecated
    String getName();

    /**
     * Is this build the one that's currently executing?
     *
     * @deprecated Compare {@link #getBuildPath()} with the build path of the current build instead.
     */
    @Deprecated
    boolean isCurrentBuild();
}
