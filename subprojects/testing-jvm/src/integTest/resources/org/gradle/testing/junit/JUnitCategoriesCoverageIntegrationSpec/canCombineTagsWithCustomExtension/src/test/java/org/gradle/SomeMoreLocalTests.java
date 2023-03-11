/*
 * Copyright 2013 the original author or authors.
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

package org.gradle;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Locale;

@ExtendWith(org.gradle.Locales.class)
@Tag("org.gradle.CategoryA")
public class SomeMoreLocalTests {
    @TestTemplate
    public void someMoreTest1(Locale locale) {
        System.out.println("Locale in use: " + locale);
    }

    @TestTemplate
    public void someMoreTest2(Locale locale) {
        System.out.println("Locale in use: " + locale);
    }
}
