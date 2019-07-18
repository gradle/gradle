/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.launcher;

import org.gradle.api.JavaVersion;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.launcher.bootstrap.ProcessBootstrap;

public class GradleMain {
    public static void main(String[] args) {
        try {
            UnsupportedJavaRuntimeException.assertUsingVersion("Gradle", JavaVersion.VERSION_1_8);
        } catch (UnsupportedJavaRuntimeException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
        new ProcessBootstrap().run("org.gradle.launcher.Main", args);
    }
}
