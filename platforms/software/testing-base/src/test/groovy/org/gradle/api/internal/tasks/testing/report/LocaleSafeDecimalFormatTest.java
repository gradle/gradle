/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.report;

import org.junit.After;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class LocaleSafeDecimalFormatTest {

    Locale defaultLocale = Locale.getDefault();

    @After
    public void revertLocalToDefault() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void parsesCommaEvenInRareLocales() throws Exception {
        //given
        Locale usesCommaAsDecimalSeparator = new Locale("PL", "pl", "");
        Locale.setDefault(usesCommaAsDecimalSeparator);

        //when
        BigDecimal result = new LocaleSafeDecimalFormat().parse("1.05");

        //then
        assertEquals(1.05, result.doubleValue(), 0f);
    }
}
