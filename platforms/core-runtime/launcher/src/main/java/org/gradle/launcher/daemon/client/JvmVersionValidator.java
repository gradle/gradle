/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.client;

import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.Global.class)
public class JvmVersionValidator {
    private final JvmVersionDetector versionDetector;

    public JvmVersionValidator(JvmVersionDetector versionDetector) {
        this.versionDetector = versionDetector;
    }

    public void validate(JavaInfo resolvedJvm) {
        if (resolvedJvm == Jvm.current()) {
            return;
        }

        int javaVersionMajor = versionDetector.getJavaVersionMajor(resolvedJvm);
        UnsupportedJavaRuntimeException.assertUsingVersion("Gradle", 8, javaVersionMajor);
    }
}
