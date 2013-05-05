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
package org.gradle.api.tasks.scala

import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.*

public class ScalaDocOptionsTest {

    private ScalaDocOptions docOptions = new ScalaDocOptions()

    @Test public void testOptionMapContainsDeprecation() {
        assertOnOffValue('deprecation', 'deprecation', true)
    }

    @Test public void testOptionMapContainsUnchecked() {
        assertOnOffValue('unchecked', 'unchecked', true)
    }

    @Test public void testOptionMapContainsWindowTitleIfSpecified() {
        assertSimpleStringValue('windowTitle', 'windowTitle', null, 'title-value')
    }

    @Test public void testOptionMapContainsDocTitleIfSpecified() {
        assertSimpleStringValue('docTitle', 'docTitle', null, 'title-value')
    }

    @Test public void testOptionMapContainsHeaderIfSpecified() {
        assertSimpleStringValue('header', 'header', null, 'header-value')
    }

    @Test public void testOptionMapContainsFooterIfSpecified() {
        assertSimpleStringValue('footer', 'footer', null, 'footer-value')
    }

    @Test public void testOptionMapContainsTopIfSpecified() {
        assertSimpleStringValue('top', 'top', null, 'top-value')
    }

    @Test public void testOptionMapContainsBottomIfSpecified() {
        assertSimpleStringValue('bottom', 'bottom', null, 'bottom-value')
    }

    @Test public void testOptionMapContainsStyleSheetIfSpecified() {
        String antProperty = 'styleSheet'
        assertNull(docOptions.styleSheet)
        assertFalse(docOptions.optionMap().containsKey(antProperty))
        File file = new File('abc')
        docOptions.styleSheet = file
        assertThat(docOptions.optionMap()[antProperty] as File, equalTo(file))
    }

    @Test public void testOptionMapContainsValuesForAdditionalParameters() {
        String antProperty = 'addParams'
        assertNull(docOptions.additionalParameters)
        assertFalse(docOptions.optionMap().containsKey(antProperty))

        docOptions.additionalParameters = ['-opt1', '-opt2']
        assertThat(docOptions.optionMap()[antProperty] as String, equalTo('-opt1 -opt2' as String))
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