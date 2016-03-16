/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.gosu

import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertThat

class GosuDocOptionsTest {
    private GosuDocOptions docOptions = new GosuDocOptions()

    @Test
    public void testOptionMapContainsTitleIfSpecified() {
        assertSimpleStringValue('title', 'title', null, 'title-value')
    }

    private assertOnOffValue(String fieldName, String antProperty, boolean defaultValue) {
        assertThat(docOptions."$fieldName" as boolean, equalTo(defaultValue))

        docOptions."$fieldName" = true
        assertThat(docOptions.optionMap()[antProperty] as String, equalTo('on'))

        docOptions."$fieldName" = false
        assertThat(docOptions.optionMap()[antProperty] as String, equalTo('off'))
    }

    private assertSimpleStringValue(String fieldName, String antProperty, String defaultValue, String testValue) {
        assertThat(docOptions."${fieldName}" as String, equalTo(defaultValue))
        if (defaultValue == null) {
            assertFalse(docOptions.optionMap().containsKey(antProperty))
        } else {
            assertThat(docOptions.optionMap()[antProperty] as String, equalTo(defaultValue))
        }
        docOptions."${fieldName}" = testValue
        assertThat(docOptions.optionMap()[antProperty] as String, equalTo(testValue))
    }
}
