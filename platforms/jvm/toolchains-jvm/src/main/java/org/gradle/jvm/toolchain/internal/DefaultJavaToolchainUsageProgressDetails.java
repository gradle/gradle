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

package org.gradle.jvm.toolchain.internal;

import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.jvm.toolchain.internal.operations.JavaToolchainUsageProgressDetails;

public class DefaultJavaToolchainUsageProgressDetails implements JavaToolchainUsageProgressDetails {

    private final JavaTool toolName;

    private final JvmInstallationMetadata toolchainMetadata;

    public DefaultJavaToolchainUsageProgressDetails(JavaTool toolName, JvmInstallationMetadata toolchainMetadata) {
        this.toolName = toolName;
        this.toolchainMetadata = toolchainMetadata;
    }

    @Override
    public String getToolName() {
        return toolName.getToolName();
    }

    @Override
    public JavaToolchain getToolchain() {
        JvmInstallationMetadata metadata = toolchainMetadata;
        return new JavaToolchain() {
            @Override
            public String getJavaVersion() {
                return metadata.getJavaVersion();
            }

            @Override
            public String getJavaVendor() {
                return metadata.getVendor().getDisplayName();
            }

            @Override
            public String getRuntimeName() {
                return metadata.getRuntimeName();
            }

            @Override
            public String getRuntimeVersion() {
                return metadata.getRuntimeVersion();
            }

            @Override
            public String getJvmName() {
                return metadata.getJvmName();
            }

            @Override
            public String getJvmVersion() {
                return metadata.getJvmVersion();
            }

            @Override
            public String getJvmVendor() {
                return metadata.getJvmVendor();
            }

            @Override
            public String getArchitecture() {
                return metadata.getArchitecture();
            }
        };
    }

    public enum JavaTool {
        COMPILER(JavaCompiler.class.getSimpleName()),
        LAUNCHER(JavaLauncher.class.getSimpleName()),
        JAVADOC(JavadocTool.class.getSimpleName());

        private final String toolName;

        JavaTool(String toolName) {
            this.toolName = toolName;
        }

        public String getToolName() {
            return toolName;
        }
    }

}
