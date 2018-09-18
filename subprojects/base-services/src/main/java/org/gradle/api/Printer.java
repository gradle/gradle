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

package org.gradle.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.UUID;

public class Printer {
    private static PrintStream ps;

    static {
        try {
            ps = new PrintStream(new File("C:/output-" + UUID.randomUUID().toString() + ".txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void print(String message) {
        if (ps == null) {
            return;
        }
        ps.println(message);
        new Exception().printStackTrace(ps);
    }

    public static void print(Throwable t) {
        if (ps == null) {
            return;
        }
        t.printStackTrace(ps);
    }
}
