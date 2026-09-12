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

package org.gradle.api.problems.internal;

/**
 * Descriptions of the predefined problem groups. Single source of truth for {@code ProblemGroup#getDescription()}
 * of predefined groups; the Javadoc on the public group types repeats the same text.
 */
public final class PredefinedProblemGroupDescriptions {

    public static final String UNDEFINED_TEMPLATE = "Problems without an explicitly defined %s sub-group";

    public static final String GRADLE = "Initialization, configuration, and execution of the Gradle runtime, integration of built-in and third-party plugins with Gradle.";
    public static final String GRADLE_BUILD_CACHE = "Caching of build outputs, including cache key calculation, local and remote cache storage, cache entry retrieval, or cache configuration.";
    public static final String GRADLE_BUILD_DEFINITION = "Configuration of settings and projects, including missing, invalid, or conflicting property values and declarations.";
    public static final String GRADLE_BUILD_LOGIC = "Gradle plugin code interacting with the build model, including registration and configuration of tasks, declaration of their inputs, outputs, cacheability, or calling other plugins' API.";
    public static final String GRADLE_CONFIGURATION_CACHE = "Caching of the configuration phase, including configuration inputs, serialization of the task graph, or reports of task incompatibilities.";
    public static final String GRADLE_DEPRECATION = "Usage of deprecated Gradle and plugins APIs, features, or behaviors scheduled for removal in a future version.";
    public static final String GRADLE_DSL_EVALUATION = "Parsing, compilation, and application of Gradle DSL files.";
    public static final String GRADLE_INVOCATION = "Build entry points such as CLI or Tooling API, including command-line options and arguments, environment variables, requested tasks, or task options.";
    public static final String GRADLE_ISOLATED_PROJECTS = "Isolation of project configuration by forbidding cross-project access, including direct access or hierarchical properties access.";
    public static final String GRADLE_PLUGIN_VALIDATION = "Validation of plugins, including incorrect use of caching or input annotations on tasks or artifact transforms.";
    public static final String DEPENDENCIES = "Declaration, resolution, locking, and verification of dependencies required by projects or the Gradle runtime.";
    public static final String DEPENDENCIES_DECLARATION = "Dependency declarations, including string notations, rich dependencies, constraints, platforms, or version catalogs; repository declarations, including Maven, Ivy, or content filtering.";
    public static final String DEPENDENCIES_LOCKING = "Dependency versions locking, including lock files generation, or enforcement of resolved versions.";
    public static final String DEPENDENCIES_GRAPH_RESOLUTION = "Variant-aware dependency graph resolution, including dependency substitutions, metadata processing, resolution rules, attribute matching, or conflict resolution.";
    public static final String DEPENDENCIES_ARTIFACT_RESOLUTION = "Artifact files resolution from the dependency graph, including compiled libraries, JAR files, AAR files, DLLs, or ZIPs.";
    public static final String DEPENDENCIES_VERIFICATION = "Dependency integrity and authenticity verification, including verification metadata file, checksum verification, signature checks, or trusted key management.";
    public static final String TRANSFORMATION = "Generation or manipulation of code, binaries, or resources before running or packaging.";
    public static final String TRANSFORMATION_BINARY = "Generation or manipulation of binaries, such as libraries or executables.";
    public static final String TRANSFORMATION_CODE = "Generation or manipulation of code, such as mappers or parsers.";
    public static final String TRANSFORMATION_RESOURCE = "Generation or manipulation of resources, such as texts, images, or documents.";
    public static final String COMPILATION = "Code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.";
    public static final String COMPILATION_CPP = "C++ code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.";
    public static final String COMPILATION_GROOVY = "Groovy code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.";
    public static final String COMPILATION_JAVA = "Java code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.";
    public static final String COMPILATION_KOTLIN = "Kotlin code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.";
    public static final String COMPILATION_SCALA = "Scala code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.";
    public static final String COMPILATION_SWIFT = "Swift code compilation, including the compiler's configuration, compiler invocation, or compiler plugins.";
    public static final String EXTERNAL_PROCESSES = "External process invocations of compiled applications, native tools, and other runnable artifacts.";
    public static final String EXTERNAL_PROCESSES_APPLICATION = "Execution of compiled applications, including startup, runtime behavior, and exit status.";
    public static final String EXTERNAL_PROCESSES_SERVICE = "Lifecycle management of service processes during the build, including startup, shutdown, and health checks.";
    public static final String EXTERNAL_PROCESSES_TOOL = "Invocation of external tools as part of the build, including tool discovery and execution.";
    public static final String PACKAGING = "Assembling products into various runnable and deployable forms, including creating binaries, archives, installers, or container images, and signing them.";
    public static final String PACKAGING_DISTRIBUTIONS = "Packaging of resources, executables, and launch infrastructure into shippable formats, including archives, container images, distribution packages, or installers.";
    public static final String PACKAGING_JAR = "Creation of JARs and manifests.";
    public static final String PACKAGING_NATIVE_LINKING = "Linking of native binaries, including linker configuration, execution, or symbol resolution.";
    public static final String PACKAGING_SIGNING = "Configuration and execution of signing, including certificate management, keystore setup, or signature generation.";
    public static final String VERIFICATION = "Verification of software correctness, quality, and compliance through testing, static analysis, code coverage, linting, and other verification mechanisms that ensure the software meets specified requirements and standards, including setup and configuration of required tools and frameworks.";
    public static final String VERIFICATION_CODE_COVERAGE = "Instrumentation, measurement, and enforcement of code coverage thresholds across test executions.";
    public static final String VERIFICATION_CODE_QUALITY = "Inspection of source code for style and best-practice violations or potential defects without execution.";
    public static final String VERIFICATION_SECURITY = "Detection of vulnerabilities, insecure configurations, credential leaks, or compliance violations in code or dependencies.";
    public static final String VERIFICATION_TESTING = "Execution of tests, including test framework configuration, test runner invocation, test environment setup, or assertion failures.";
    public static final String DOCUMENTATION = "Production of documentation of any form, including API docs, manuals, or websites.";
    public static final String DOCUMENTATION_JAVADOC = "Configuration and generation of Javadoc API documentation, including tag validation, doclets, or reference resolution.";
    public static final String DOCUMENTATION_GROOVYDOC = "Configuration and generation of Groovy API documentation, including tag validation, doclets, or reference resolution.";
    public static final String DOCUMENTATION_SCALADOC = "Configuration and generation of Scala API documentation, including tag validation, doclets, or reference resolution.";
    public static final String PROVISIONING = "Making resources available for use and managing them, including tools and toolchains, services, or infrastructure.";
    public static final String PROVISIONING_INFRASTRUCTURE = "Provisioning and lifecycle management of infrastructure resources, including cloud resources, containers, or services.";
    public static final String PROVISIONING_TOOLS_AND_TOOLCHAINS = "Discovery, download, installation, and configuration of tools and toolchains required for the build, including SDKs or arbitrary tools.";
    public static final String DELIVERY = "Making products available to users, including publishing software components, deploying applications, or uploading documentation.";
    public static final String DELIVERY_PUBLISHING = "Making products available for others to fetch, such as Maven or Ivy artifacts in repositories, libraries in package registries, container images in registries, applications in stores, or documentation and distributions on websites.";
    public static final String DELIVERY_DEPLOYMENT = "Making products ready for others to use, such as deploying containers and Kubernetes manifests, application archives like WARs or EARs on application servers, serverless functions, or static sites.";
    public static final String OTHERS = "Problems that do not fit in any other group.";

    private PredefinedProblemGroupDescriptions() {
    }

    public static String undefined(String parentName) {
        return String.format(UNDEFINED_TEMPLATE, parentName);
    }
}
