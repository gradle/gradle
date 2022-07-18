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

import org.gradle.jvm.toolchain.internal.operations.JavaToolchainUsageProgressDetails;

public class DefaultJavaToolchainUsageProgressDetails implements JavaToolchainUsageProgressDetails {

    private final JavaTool toolName;

    private final org.gradle.jvm.toolchain.internal.JavaToolchain toolchain;

    public DefaultJavaToolchainUsageProgressDetails(JavaTool toolName, org.gradle.jvm.toolchain.internal.JavaToolchain toolchain) {
        this.toolName = toolName;
        this.toolchain = toolchain;
    }

    @Override
    public String getToolName() {
        return toolName.getToolName();
    }

    @Override
    public JavaToolchain getToolchain() {
        return new JavaToolchain() {
            @Override
            public String getLanguageVersion() {
                return toolchain.getMetadata().getLanguageVersion().toString();
            }

            @Override
            public String getVendor() {
                return toolchain.getVendor();
            }
        };
    }

    public enum JavaTool {
        COMPILER("javac"),
        LAUNCHER("java"),
        JAVADOC("javadoc");

        private final String toolName;

        JavaTool(String toolName) {
            this.toolName = toolName;
        }

        public String getToolName() {
            return toolName;
        }
    }

}
