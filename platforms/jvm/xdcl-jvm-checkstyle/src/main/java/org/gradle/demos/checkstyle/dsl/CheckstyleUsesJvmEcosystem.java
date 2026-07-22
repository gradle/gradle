/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.checkstyle.dsl;

import org.gradle.demos.java.dsl.JavaComponent;

public class CheckstyleUsesJvmEcosystem {
    // Workaround for Gradle build project health check: if a dependency is only used in XDCL,
    // it is considered an unused dependency
    public CheckstyleUsesJvmEcosystem(JavaComponent javaComponent) {
    }
}
