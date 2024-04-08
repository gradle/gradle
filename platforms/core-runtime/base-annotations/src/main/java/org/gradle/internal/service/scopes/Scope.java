/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service.scopes;

/**
 * @see ServiceScope
 */
public interface Scope {

    /**
     * These services are reused across builds in the same process.
     *
     * <p>Global services are visible to all other services.</p>
     */
    interface Global extends Scope {}

    /**
     * These services are reused across builds in the same process while the Gradle user home directory remains unchanged.
     * The services are closed when the Gradle user home directory changes.
     *
     * <p>{@link Global} and parent scope services are visible to {@link UserHome} scope services, but not vice versa.</p>
     */
    interface UserHome extends Global {}

    /**
     * These services are reused across build sessions.
     * <p>
     * Generally, one regular Gradle invocation is conceptually a session.
     * However, the GradleBuild task is currently implemented in such a way that it uses a discrete session.
     * Having the GradleBuild task reuse the outer session is complicated because it <a href="https://github.com/gradle/gradle/issues/4559">may use a different Gradle user home</a>.
     *
     * <p>{@link Global} and parent scope services are visible to {@link CrossBuildSession} scope services, but not vice versa.</p>
     */
    interface CrossBuildSession extends Global {}

    /**
     * These services are reused across build invocations in a session.
     *
     * A build session can be long-lived in a continuous build (where these services would be reused) or short-lived in a
     * regular, single build.
     *
     * <p>{@link UserHome}, {@link CrossBuildSession} and parent scope services are visible to {@link BuildSession} scope services, but not vice versa.</p>
     */
    interface BuildSession extends UserHome, CrossBuildSession {}

    /**
     * These services are recreated when in continuous build and shared across all nested builds.
     * They are closed when the build invocation is completed.
     *
     * <p>{@link BuildSession} and parent scope services are visible to {@link BuildTree} scope services, but not vice versa.</p>
     */
    interface BuildTree extends BuildSession {}

    /**
     * These services are created once per {@code org.gradle.api.initialization.Settings} the beginning of the build invocation
     * These services are closed at the end of the build invocation.
     *
     * <p>{@link BuildTree} and parent scope services are visible to {@link Build} scope services, but not vice versa.</p>
     */
    interface Build extends BuildTree {}

    /**
     * These services are created once per {@code org.gradle.api.invocation.Gradle} at the beginning of the build invocation.
     * These services are closed at the end of the build invocation.
     *
     * <p>{@link Build} and parent scope services are visible to {@link Gradle} scope services, but not vice versa.</p>
     */
    interface Gradle extends Build {}

    /**
     * These services are created once per project per build invocation.
     * These services are closed at the end of the build invocation.
     *
     * <p>{@link Gradle} and parent scope services are visible to {@link Project} scope services, but not vice versa.</p>
     */
    interface Project extends Gradle {}
}
