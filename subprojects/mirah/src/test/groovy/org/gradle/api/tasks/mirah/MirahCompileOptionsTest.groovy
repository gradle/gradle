/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.mirah

import org.junit.Before
import org.junit.Test
import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.*

class MirahCompileOptionsTest {

    private MirahCompileOptions compileOptions

    @Before void setUp() {
        compileOptions = new MirahCompileOptions()
    }

    @Test void testOptionMapContainsFailOnError() {
        assertBooleanValue('failOnError', true)
    }

    @Test void testOptionMapContainsDeprecation() {
        assertBooleanValue('deprecation', true)
    }

    @Test void testOptionMapContainsUnchecked() {
        assertBooleanValue('unchecked', true)
    }

    @Test void testOptionMapContainsDebugLevelIfSpecified() {
        assertSimpleStringValue('debugLevel', null, 'line')
    }

    @Test void testOptionMapContainsEncodingIfSpecified() {
        assertSimpleStringValue('encoding', null, 'utf8')
    }

    @Test void testOptionMapContainsForce() {
        assertSimpleStringValue('force', 'never', 'changed')
    }

    @Test void testOptionMapDoesNotContainTargetCompatibility() {
        assert !compileOptions.optionMap().containsKey("target")
    }

    private assertBooleanValue(String fieldName, boolean defaultValue) {
        assertThat(compileOptions."$fieldName" as boolean, equalTo(defaultValue))
    }

    private assertOnOffValue(String fieldName, boolean defaultValue) {
        assertThat(compileOptions."$fieldName" as boolean, equalTo(defaultValue))
    }

    private assertSimpleStringValue(String fieldName, String defaultValue, String testValue) {
        assertThat(compileOptions."${fieldName}" as String, equalTo(defaultValue))
    }

}