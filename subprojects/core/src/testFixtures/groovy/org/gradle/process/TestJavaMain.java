/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.process;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.Map;

/**
 * An executable Java class that can be used standalone to verify javaexec routines.
 */
public class TestJavaMain {
    public static String getClassLocation() {
        try {
            return new File(TestJavaMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            System.out.println("ARGUMENTS: " + String.join(" ", args));
        }
        System.out.println("ENVIRONMENT:");
        printMap(System.getenv(), "TEST_");

        System.out.println("PROPERTIES:");
        printMap(System.getProperties(), "test.");
    }

    private static void printMap(Map<?, ?> items, String prefix) {
        items.entrySet().stream().filter(e -> String.valueOf(e.getKey()).startsWith(prefix)).sorted(comparingByStringifiedKey()).forEach(e -> {
            System.out.printf("   %s=%s\n", e.getKey(), e.getValue());
        });
    }

    private static Comparator<Map.Entry<?, ?>> comparingByStringifiedKey() {
        return Comparator.comparing(entry -> String.valueOf(entry.getKey()));
    }
}
