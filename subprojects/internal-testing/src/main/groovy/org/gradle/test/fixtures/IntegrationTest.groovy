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

package org.gradle.test.fixtures

import org.junit.jupiter.api.Tag

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * This annotation is a marker and guarantees all integration tests are tagged.
 *
 * When we use JUnit Platform `includeTags('SomeTag')`, all spock tests are excluded:
 * https://github.com/spockframework/spock/issues/1288 . As a workaround,
 * we tag all non-spock integration tests and use `includeTags(none() | SomeTag)` to make
 * sure spock engine tests are executed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@Tag("org.gradle.test.fixtures.IntegrationTest")
@interface IntegrationTest {
}
