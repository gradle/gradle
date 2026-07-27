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

/// Test annotations that gate integration tests by Gradle execution mode (Configuration Cache, Isolated Projects).
///
/// Each mode has two annotations: `ToBeFixedFor*` for tests that should eventually pass under the mode,
/// and `UnsupportedWith*` for tests covering features not intended to ever support the mode.
/// They apply only when the corresponding mode is active for the current test run; in any other mode they are ignored.
///
/// ## Choosing the right annotation
///
/// | Intention | Annotation | Default effect under the mode |
/// |---|---|---|
/// | Test/feature should eventually work under the mode | [ToBeFixedForConfigurationCache] / [ToBeFixedForIsolatedProjects] | Expect failure (flipped result) |
/// | Feature is incompatible by design and will not be fixed | [UnsupportedWithConfigurationCache] / [UnsupportedWithIsolatedProjects] | Skip |
///
/// On `ToBeFixedFor*`, set `skipBecause = "..."` to skip instead of expecting failure
/// (useful for flaky or hanging tests). `UnsupportedWith*` always skips.
///
/// ## Placement
///
/// Annotations can be placed on a test method or on a spec class. When placed on a class, they apply to every
/// feature in that class.
///
/// ## Scoping the annotation
///
/// Both filters are conjunctive: an iteration is affected only if it satisfies *all* filters that are set.
///
/// - **`bottomSpecs`** — simple class names of leaf specs the annotation should apply to.
///   Useful when annotating a base spec: only the listed subclasses are gated. Empty means "all subclasses".
/// - **`iterationMatchers`** — regular expressions matched against each iteration's display name
///   (Spock `where:` blocks). The annotation applies only to iterations matching at least one regex.
///   Empty means "all iterations".
///
/// ## How it works
///
/// The four annotations are Spock `@ExtensionAnnotation`s handled by [GradleModeTestingExtension].
/// For JUnit 4 tests, apply [GradleModeTestingRule] as a `@Rule`.
/// The currently-active mode is determined by [GradleModeTesting], which delegates to
/// [org.gradle.integtests.fixtures.executer.GradleContextualExecuter].
package org.gradle.integtests.fixtures.modes;
