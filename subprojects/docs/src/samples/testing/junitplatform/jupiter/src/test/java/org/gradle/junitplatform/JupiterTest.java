/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.junitplatform;

import org.junit.jupiter.api.*;

public class JupiterTest {
    @Test
    public void ok() {
        System.out.println("Hello from JUnit Jupiter!");
    }

    @RepeatedTest(2)
    public void repeated() {
        System.out.println("This will be repeated!");
    }

    @BeforeEach
    public void beforeEach() {
        System.out.println("This will be called before each method!");
    }

    @BeforeAll
    public static void beforeAll() {
        System.out.println("This will be called before all methods!");
    }

    @Disabled
    @Test
    public void disabled() {
        throw new RuntimeException("This won't happen!");
    }
}
