/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * The problem groups: the predefined hierarchy, and the entry point for creating sub-groups below it.
 * <p>
 * Problems are organized in a fixed hierarchy of root groups, each with predefined sub-groups. Producers place a problem in the
 * most specific predefined group matching the activity the problem is about, or create their own sub-group below a predefined
 * group when none matches. Group names describe <em>what</em> a problem is about, not <em>where</em> or <em>when</em> it happened.
 * <p>
 * To pick a group:
 * <ul>
 *     <li>Find the root group matching the activity (for example {@link #getCompilation()} for compiler problems).</li>
 *     <li>Find the matching predefined sub-group and report the problem there, for example
 *     {@code problems.getGroups().getCompilation().getJava().problem("Unused import")}.</li>
 *     <li>If no predefined sub-group matches, create one with {@link RootProblemGroup#group(String)}, for example
 *     {@code problems.getGroups().getTransformation().group("KMP").problem("...")}, or use the {@code Undefined} sub-group.</li>
 *     <li>If no root group matches, create a sub-group below {@link #getOthers()}, or use its {@code Undefined} sub-group.</li>
 * </ul>
 * If several groups match, pick the one closest to the context of the producer.
 * <p>
 * Group names are case-sensitive. Creating a group with the exact name of a predefined sibling returns the predefined group;
 * any other name creates a new group. The name {@code Undefined} is reserved for the synthetic {@code Undefined} groups.
 * <p>
 * Not intended for implementation outside of Gradle. New members may be added in future versions.
 *
 * @see Problems#getGroups()
 * @since 9.9.0
 */
@Incubating
public interface ProblemGroups {

    /**
     * The {@code Gradle} root group.
     * <p>
     * Initialization, configuration, and execution of the Gradle runtime, integration of built-in and third-party
     * plugins with Gradle.
     *
     * @since 9.9.0
     */
    GradleProblemGroup getGradle();

    /**
     * The {@code Dependencies} root group.
     * <p>
     * Declaration, resolution, locking, and verification of dependencies required by projects or the Gradle runtime.
     *
     * @since 9.9.0
     */
    DependenciesProblemGroup getDependencies();

    /**
     * The {@code Transformation} root group.
     * <p>
     * Generation or manipulation of code, binaries, or resources before running or packaging.
     *
     * @since 9.9.0
     */
    TransformationProblemGroup getTransformation();

    /**
     * The {@code Compilation} root group.
     * <p>
     * Code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.
     *
     * @since 9.9.0
     */
    CompilationProblemGroup getCompilation();

    /**
     * The {@code External Processes} root group.
     * <p>
     * External process invocations of compiled applications, native tools, and other runnable artifacts.
     *
     * @since 9.9.0
     */
    ExternalProcessesProblemGroup getExternalProcesses();

    /**
     * The {@code Packaging} root group.
     * <p>
     * Assembling products into various runnable and deployable forms, including creating binaries, archives,
     * installers, or container images, and signing them.
     *
     * @since 9.9.0
     */
    PackagingProblemGroup getPackaging();

    /**
     * The {@code Verification} root group.
     * <p>
     * Verification of software correctness, quality, and compliance through testing, static analysis, code coverage,
     * linting, and other verification mechanisms that ensure the software meets specified requirements and
     * standards, including setup and configuration of required tools and frameworks.
     *
     * @since 9.9.0
     */
    VerificationProblemGroup getVerification();

    /**
     * The {@code Documentation} root group.
     * <p>
     * Production of documentation of any form, including API docs, manuals, or websites.
     *
     * @since 9.9.0
     */
    DocumentationProblemGroup getDocumentation();

    /**
     * The {@code Provisioning} root group.
     * <p>
     * Making resources available for use and managing them, including tools and toolchains, services, or infrastructure.
     *
     * @since 9.9.0
     */
    ProvisioningProblemGroup getProvisioning();

    /**
     * The {@code Delivery} root group.
     * <p>
     * Making products available to users, including publishing software components, deploying applications, or
     * uploading documentation.
     *
     * @since 9.9.0
     */
    DeliveryProblemGroup getDelivery();

    /**
     * The {@code Others} root group.
     * <p>
     * Problems that do not fit in any other group.
     *
     * @since 9.9.0
     */
    OthersProblemGroup getOthers();
}
