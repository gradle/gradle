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
package org.gradle.api;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class JavaVersionTest {
    @Test
    public void toStringReturnsVersion() {
        assertThat(JavaVersion.VERSION_1_3.toString(), equalTo("1.3"));
        assertThat(JavaVersion.VERSION_1_4.toString(), equalTo("1.4"));
        assertThat(JavaVersion.VERSION_1_5.toString(), equalTo("1.5"));
        assertThat(JavaVersion.VERSION_1_6.toString(), equalTo("1.6"));
        assertThat(JavaVersion.VERSION_1_7.toString(), equalTo("1.7"));
    }

    @Test
    public void convertsStringToVersion() {
        assertThat(JavaVersion.toVersion("1.3"), equalTo(JavaVersion.VERSION_1_3));
        assertThat(JavaVersion.toVersion("1.5"), equalTo(JavaVersion.VERSION_1_5));
        assertThat(JavaVersion.toVersion("1.5.4"), equalTo(JavaVersion.VERSION_1_5));
        assertThat(JavaVersion.toVersion("1.5_4"), equalTo(JavaVersion.VERSION_1_5));
        assertThat(JavaVersion.toVersion("1.5.0.4_b109"), equalTo(JavaVersion.VERSION_1_5));

        assertThat(JavaVersion.toVersion("5"), equalTo(JavaVersion.VERSION_1_5));
        assertThat(JavaVersion.toVersion("6"), equalTo(JavaVersion.VERSION_1_6));
        assertThat(JavaVersion.toVersion("7"), equalTo(JavaVersion.VERSION_1_7));
    }

    @Test
    public void failsToConvertStringToVersionForUnknownVersion() {
        conversionFails("1");
        conversionFails("2");

        conversionFails("8");

        conversionFails("a");
        conversionFails("");
        conversionFails("  ");

        conversionFails("1.54");
        conversionFails("1.10");
        conversionFails("2.0");
        conversionFails("1_4");
    }

    @Test
    public void convertsVersionToVersion() {
        assertThat(JavaVersion.toVersion(JavaVersion.VERSION_1_4), equalTo(JavaVersion.VERSION_1_4));
    }
    
    @Test
    public void convertsNumberToVersion() {
        assertThat(JavaVersion.toVersion(1.3), equalTo(JavaVersion.VERSION_1_3));
        assertThat(JavaVersion.toVersion(1.5), equalTo(JavaVersion.VERSION_1_5));
        assertThat(JavaVersion.toVersion(5), equalTo(JavaVersion.VERSION_1_5));
        assertThat(JavaVersion.toVersion(6), equalTo(JavaVersion.VERSION_1_6));
        assertThat(JavaVersion.toVersion(7), equalTo(JavaVersion.VERSION_1_7));
        assertThat(JavaVersion.toVersion(1.7), equalTo(JavaVersion.VERSION_1_7));
    }
    
    @Test
    public void failsToConvertNumberToVersionForUnknownVersion() {
        conversionFails(1);
        conversionFails(2);
        conversionFails(8);
        conversionFails(1.21);
        conversionFails(2.0);
        conversionFails(4.2);
    }

    @Test
    public void convertsNullToNull() {
        assertThat(JavaVersion.toVersion(null), nullValue());
    }

    private void conversionFails(Object value) {
        try {
            JavaVersion.toVersion(value);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("Could not determine java version from '" + value + "'."));
        }
    }
}
