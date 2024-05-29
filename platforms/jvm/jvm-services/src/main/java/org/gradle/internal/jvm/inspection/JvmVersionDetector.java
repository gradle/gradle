/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Probes a JVM installation to determine the Java version it provides.
 */
@ServiceScope(Scope.Global.class)
public interface JvmVersionDetector {
    /**
     * Probes the Java version for the given JVM installation.
     */
    int getJavaVersionMajor(JavaInfo jvm);

    /**
     * Probes the Java version for the given `java` command.
     */
    int getJavaVersionMajor(String javaCommand);
}
