/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.artifacts.dsl;

import org.gradle.api.Incubating;

/**
 * Dependency APIs for using <a href="https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures">Test Fixtures</a> in {@code dependencies} blocks.
 *
 * <p>
 * NOTE: This API is <strong>incubating</strong> and is likely to change until it's made stable.
 * </p>
 *
 * @since 7.6
 */
@Incubating
public interface TestFixturesDependencyModifiers {
    TestFixturesDependencyModifier getTestFixtures();
    interface TestFixturesDependencyModifier extends DependencyModifier {
    }
}
