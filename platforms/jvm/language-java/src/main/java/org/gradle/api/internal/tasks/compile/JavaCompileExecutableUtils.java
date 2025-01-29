/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.SpecificInstallationToolchainSpec;

import javax.annotation.Nullable;
import java.io.File;

public class JavaCompileExecutableUtils {

    @Nullable
    public static JavaToolchainSpec getExecutableOverrideToolchainSpec(JavaCompile task, PropertyFactory propertyFactory) {
        if (!task.getOptions().isFork()) {
            return null;
        }

        ForkOptions forkOptions = task.getOptions().getForkOptions();
        @SuppressWarnings("deprecation")
        File customJavaHome = DeprecationLogger.whileDisabled(forkOptions::getJavaHome);
        if (customJavaHome != null) {
            return SpecificInstallationToolchainSpec.fromJavaHome(propertyFactory, customJavaHome);
        }

        String customExecutable = forkOptions.getExecutable();
        if (customExecutable != null) {
            return SpecificInstallationToolchainSpec.fromJavaExecutable(propertyFactory, customExecutable);
        }

        return null;
    }
}
