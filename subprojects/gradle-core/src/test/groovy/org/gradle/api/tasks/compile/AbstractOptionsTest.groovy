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
package org.gradle.api.tasks.compile

import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

public class AbstractOptionsTest {
    private final TestOptions options = new TestOptions()

    @Test
    public void hasEmptyOptionsMapWhenEverythingIsNull() {
        assertThat(options.optionMap(), isEmptyMap())
    }

    @Test
    public void optionsMapIncludesNonNullValues() {
        assertThat(options.optionMap(), isEmptyMap())

        options.intProp = 9
        Map expected = new LinkedHashMap();
        expected.intProp = 9
        assertThat(options.optionMap(), equalTo(expected))

        options.stringProp = 'string'
        expected.stringProp = 'string'
        assertThat(options.optionMap(), equalTo(expected))
    }
}

class TestOptions extends AbstractOptions {
    Integer intProp
    String stringProp
    Object objectProp
}