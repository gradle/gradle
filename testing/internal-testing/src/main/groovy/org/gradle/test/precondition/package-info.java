/*
 * Copyright 2023 the original author or authors.
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

/**
 * Test precondition framework for conditionally skipping tests based on environment requirements.
 *
 * <p>Use {@link Requires @Requires} annotations with {@link TestPrecondition} implementations to
 * skip tests when their preconditions are not met. Precondition classes are organized by what they check:</p>
 *
 * <p><b>In {@code testing/internal-testing} (available to all tests):</b></p>
 * <ul>
 *     <li>{@code OsTestPreconditions} &mdash; operating system detection (Windows, macOS, Linux, etc.)</li>
 *     <li>{@code FileSystemTestPreconditions} &mdash; filesystem capabilities (symlinks, permissions, case sensitivity)</li>
 *     <li>{@code JdkVersionTestPreconditions} &mdash; running JVM version and vendor</li>
 *     <li>{@code TestEnvironmentPreconditions} &mdash; CI infrastructure and external tooling (Docker, XCode, network, Groovy version)</li>
 *     <li>{@code PluginTestPreconditions} &mdash; shell availability (bash, dash, shellcheck)</li>
 * </ul>
 *
 * <p><b>In {@code testing/internal-distribution-testing} (available to integration tests):</b></p>
 * <ul>
 *     <li>{@code InstalledJdkTestPreconditions} &mdash; JDK/JRE installations available on disk</li>
 *     <li>{@code TestExecutionPreconditions} &mdash; test execution context (executor mode, daemon, parallel, configuration cache)</li>
 * </ul>
 *
 * <p><b>Domain-specific (in their respective modules):</b></p>
 * <ul>
 *     <li>{@code NativeTestPreconditions} &mdash; native toolchain availability</li>
 *     <li>{@code SigningTestPreconditions} &mdash; GPG availability</li>
 *     <li>{@code TestKitTestPreconditions} &mdash; TestKit version availability</li>
 *     <li>{@code SmokeTestPreconditions} &mdash; smoke test JVM spec</li>
 * </ul>
 *
 * <p>All precondition classes live in the {@code org.gradle.test.preconditions} package.</p>
 *
 * @see TestPrecondition
 * @see Requires
 */
@GroovyNullMarked
package org.gradle.test.precondition;

import org.gradle.util.GroovyNullMarked;
