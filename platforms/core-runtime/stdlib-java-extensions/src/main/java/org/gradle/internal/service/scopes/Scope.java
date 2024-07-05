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
 * Scopes represent the state, services and lifetime of each activity performed by Gradle as part of
 * executing a <em>Gradle invocation</em> against a <em>user build</em>.
 * <p>
 * <b>User build</b> is a collection of files on disk that constitutes the user's software project,
 * building of which is orchestrated by Gradle.
 * It can be a Java library, a backend microservice, or an enterprise monorepo.
 * <p>
 * <b>Gradle invocation</b> is a direct or indirect request of the user to invoke Gradle
 * over a given <em>user build</em> to do some work.
 * It can be a command-line invocation or a Tooling API client request (e.g. an IDE sync).
 *
 * <pre>
 *            Global
 *     ┌────────┴─────────┐
 *  UserHome      CrossBuildSession
 *     └────────┬─────────┘
 *         BuildSession
 *              │
 *          BuildTree
 *              │
 *            Build
 *              │
 *            Gradle
 *              │
 *           Project
 * </pre>
 *
 * @see ServiceScope
 */
public interface Scope {

    /**
     * Scope of the entire <em>build process</em>, e.g. a long-lived Gradle daemon.
     * <p>
     * The build process can potentially serve multiple Gradle invocations.
     * The <em>build process state</em> holds the global state of the build process and manages all the other state.
     * <p>
     * The build process state is managed by the {@code BuildProcessState} class.
     * An instance is created once for a given process.
     * <p>
     * Global services are visible to all other services.
     */
    interface Global extends Scope {}

    /**
     * The scope of a Gradle invocation particular Gradle user home directory.
     * <p>
     * When that directory changes between Gradle invocations, the state is discarded and recreated.
     * Otherwise, the state is reused between invocations.
     * <p>
     * A regular Gradle invocation deals with a single user home directory,
     * but the daemon can be potentially reused for invocations with other directories as well.
     * <p>
     * The related state is created per Gradle invocation.
     * <p>
     * {@link Global} services are visible to {@link UserHome} services and descendant scopes, but not vice versa.
     */
    interface UserHome extends Global {}

    /**
     * The scope of the state shared across {@link BuildSession build sessions}.
     * <p>
     * A regular Gradle invocation requires only one build session.
     * However, when the {@code GradleBuild} task is involved, it can create "nested" build sessions.
     * Having the {@code GradleBuild} task reuse the outer session is complicated because
     * it <a href="https://github.com/gradle/gradle/issues/4559">can use a different Gradle user home</a>.
     * <p>
     * The cross build session state is managed by the {@code CrossBuildSessionState} class.
     * An instance is created per Gradle invocation.
     *
     * <p>{@link Global} services are visible to {@link CrossBuildSession} services and descendant scopes, but not vice versa.
     */
    interface CrossBuildSession extends Global {}

    /**
     * The scope of a build session.
     * <p>
     * A <em>build session</em> represents a single invocation of Gradle, for example when you run {@code gradlew build}.
     * A session runs the build one or more times.
     * For example, when continuous build is enabled, the session may run the build many times,
     * but when it is disabled, the session will run the build only once.
     * <p>
     * The build session state is managed by the {@code BuildSessionState} class.
     * An instance is created at the start of a Gradle invocation and discarded at the end of that invocation.
     * <p>
     * {@link UserHome}, {@link CrossBuildSession} and their parent services are visible to
     * {@link BuildSession} services and descendant scopes, but not vice versa.
     */
    interface BuildSession extends UserHome, CrossBuildSession {}

    /**
     * The scope of a single <em>build execution</em> within a {@link BuildSession build session}.
     * <p>
     * <em>Build tree</em> is another name for the build definition ({@code BuildDefinition}),
     * which corresponds to composite build.
     * <p>
     * The <em>build tree state</em> holds the state for the entire build definition for a single build execution within a session.
     * The build tree state is managed by the {@code BuildTreeState} class.
     * An instance is created at the start of a build execution and discarded at the end of that execution.
     * <p>
     * {@link BuildSession} and its parent services are visible to {@link BuildTree} services and descendant scopes, but not vice versa.
     */
    interface BuildTree extends BuildSession {}

    /**
     * The scope of a single <em>build</em> within a {@link BuildTree build tree}.
     * <p>
     * The <em>build state</em> holds the state for a <em>build</em> within the <em>build definition</em> for a single <em>build execution</em>,
     * and is contained by the <em>build tree state</em>.
     * <p>
     * The build state is managed by the {@code BuildState} class.
     * An instance is created for each build in the build definition, once per build execution and is discarded at the end of that execution.
     * <p>
     * There is one-to-one correspondence between a <em>build</em> and its {@code Settings}.
     * However, the build state also contains the state exposed to init scripts,
     * evaluation of which happens prior to the evaluation of settings.
     * <p>
     * {@link BuildTree} and parent services are visible to {@link Build} services and descendant scopes, but not vice versa.
     */
    interface Build extends BuildTree {}

    /**
     * The scope of the {@code org.gradle.api.invocation.Gradle} instance state.
     * <p>
     * The <em>Gradle state</em> is being merged into the {@link Build build state} and is mostly empty.
     * <p>
     * {@link Build} and parent services are visible to {@link Gradle} services and descendant scopes, but not vice versa.
     */
    interface Gradle extends Build {}

    /**
     * The scope of a single project within a {@link Build build}.
     * <p>
     * The <em>project state</em> holds the state for a project for a single build execution,
     * and is contained by the build state (and not the state of the parent project).
     * <p>
     * The project state is managed by the {@code ProjectState} class.
     * It is created for each project in the build definition,
     * once per build execution and is discarded at the end of the execution.
     * <p>
     * {@link Gradle} and parent scope services are visible to {@link Project} scope services, but not vice versa.
     */
    interface Project extends Gradle {}
}
