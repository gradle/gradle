/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.test.fixtures

import org.junit.jupiter.api.Tag

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Marks a test as flaky. The flaky tests are quarantined to run
 * at a special stage so it won't interrupt normal build pipeline.
 *
 * For Spock tests, including/excluding tests annotated by this annotation is handled by `SpockConfig.groovy` in classpath.
 * For JUnit Jupiter tests, because it's a <a href="https://junit.org/junit5/docs/current/user-guide/#writing-tests-meta-annotations">Meta-Annotation</a>,
 *   tests annotated by this is handled by `JUnitPlatformOptions.includeTags/excludeTags`.
 * For JUnit 4 tests, because we run them in Vintage engine, we have to annotate the tests with `@Category(Flaky.class)` so it can be transparently
 *   recognized by `JUnitPlatformOptions.includeTags/excludeTags` (<a href="https://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4-categories-support">Categories Support</a>).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@Tag("org.gradle.test.fixtures.Flaky")
@interface Flaky {
    /**
     * Description or issue tracker link.
     */
    String because()
}
