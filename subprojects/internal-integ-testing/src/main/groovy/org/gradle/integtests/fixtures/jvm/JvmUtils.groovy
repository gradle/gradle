/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm

import org.gradle.api.JavaVersion
import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm


class JvmUtils {
    static Jvm findAnotherJvm() {
        def current = Jvm.current()
        AvailableJavaHomes.getAvailableJdk(new Spec<JvmInstallation>() {
            @Override
            boolean isSatisfiedBy(JvmInstallation jvm) {
                return jvm.javaHome != current.javaHome &&
                    jvm.javaVersion >= JavaVersion.VERSION_1_7 &&
                    Jvm.discovered(jvm.javaHome, jvm.javaVersion).jre != null
            }
        })
    }
}
