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
 * Each scope corresponds to some state managed by Gradle when
 * executing a <em>Gradle invocation</em> against a <em>user build</em>.
 * <p>
 * <b>User build</b> is a collection of files on disk that constitutes the user's software project,
 * building of which is orchestrated by Gradle.
 * It can be a Java library, an enterprise monorepo or anything else.
 * <p>
 * <b>Gradle invocation</b> is a direct or indirect request of the user to invoke Gradle
 * over a given <em>user build</em> to do some work.
 * It can be a command-line invocation or a Tooling API client request (e.g. an IDE sync).
 *
 * <h2>Scope hierarchy</h2>
 * The scopes are arranged in a hierarchy (with some scopes having multiple parents):
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
 *         ┌────┴────┐
 *      Project   Settings
 * </pre>
 *
 * Each scope roughly corresponds to the following user-facing concepts:
 * <ul>
 * <li>{@link Global}            — Gradle daemon process
 * <li>{@link UserHome}          — Gradle user home
 * <li>{@link CrossBuildSession} — exists mainly because of {@code GradleBuild} task
 * <li>{@link BuildSession}      — continuous build
 * <li>{@link BuildTree}         — composite build
 * <li>{@link Build}             — build in a composite build
 * <li>{@link Gradle}            — exists for historical reasons, almost empty
 * <li>{@link Settings}          — init scripts, settings script
 * <li>{@link Project}           — project in a build
 * </ul>
 *
 * There can be multiple "instances" of a scope inside one "instance" of a parent scope.
 * <p>
 * For example, in a composite build, the simplified hierarchy of state can look like:
 * <pre>
 * build tree
 * ├── root build
 * │   ├── root project
 * │   └── project
 * └── included build
 *     ├── root project
 *     └── project
 * </pre>
 *
 * <h3>Services and their visibility</h3>
 * The state in each scope is created and managed by services registered in that scope.
 * All services of a scope are assembled in a {@code ServiceRegistry}.
 * When the registry is closed all its services are closed as well and the state is discarded.
 * <p>
 * Services of all parent scopes are visible to services in a given scope.
 * For example, all {@code Global} services are visible to services in {@code UserHome} scope, but not vice versa.
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
     * The scope of a Gradle invocation with a specific Gradle user home directory.
     * <p>
     * When the user-home directory changes between subsequent Gradle invocations in the same <em>build process</em>,
     * the state of this scope is discarded and recreated.
     * Otherwise, the state is reused between invocations.
     * <p>
     * The related state is created per Gradle invocation.
     * <p>
     * {@link Global} services are visible to {@link UserHome} services and descendant scopes, but not vice versa.
     */
    interface UserHome extends Global {}

    /**
     * The scope of the state shared across {@link BuildSession build sessions}.
     * <p>
     * A regular Gradle invocation requires only one "main" build session.
     * However, when the {@code GradleBuild} task is involved, it can create "nested" build sessions.
     * Having the {@code GradleBuild} task reuse the "main" build session is complicated because
     * it <a href="https://github.com/gradle/gradle/issues/4559">can use a different Gradle user home</a>.
     * <p>
     * The cross build session state is managed by the {@code CrossBuildSessionState} class.
     * An instance is created per Gradle invocation.
     * Unlike the {@code UserHome} or {@code Global} state, this state is discarded at the end of each invocation.
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
     * The scope of the {@code org.gradle.api.invocation.Gradle} instance of a build.
     * <p>
     * The state and the services are created at the same time the {@code Gradle} instance
     * is initialized for a given <em>build</em>, and they are discarded at the end of the build execution.
     * <p>
     * The <em>Gradle state</em> is being merged into the {@link Build build state} and is mostly empty.
     * <p>
     * {@link Build} and parent services are visible to {@link Gradle} services and descendant scopes, but not vice versa.
     */
    interface Gradle extends Build {}

    /**
     * The scope that owns the settings of a build.
     * <p>
     * The settings state is managed by the {@code SettingsState} class.
     * The creation of that state implies evaluation of init scripts and settings scripts
     * of the owner-build and any builds that are included as part of a composite build.
     * <p>
     * The state is discarded at the end of the build execution.
     * <p>
     * {@link Gradle} and parent services are visible to {@link Settings} scope services, but not vice versa.
     */
    interface Settings extends Gradle {}

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
