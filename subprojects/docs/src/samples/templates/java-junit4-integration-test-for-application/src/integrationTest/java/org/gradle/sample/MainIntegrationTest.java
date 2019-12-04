/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.sample;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;
import static org.junit.Assert.*;

public class MainIntegrationTest {
    @Test public void testMain() {
        PrintStream savedOut = System.out;
        try {
            ByteArrayOutputStream outStreamForTesting = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outStreamForTesting));

            Main.main(new String[0]);

            assertEquals("Hello, World!\n", outStreamForTesting.toString());
        } finally {
            System.setOut(savedOut);
        }
    }
}
