/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.JavaVersion;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

class JavaPluginExtensionAdapters {

    static class SourceCompatibilityAdapter {
        @BytecodeUpgrade
        static JavaVersion getSourceCompatibility(JavaPluginExtension extension) {
            return extension.getSourceCompatibility().get();
        }

        @BytecodeUpgrade
        static void setSourceCompatibility(JavaPluginExtension extension, JavaVersion sourceCompatibility) {
            extension.getSourceCompatibility().set(sourceCompatibility);
        }

        @BytecodeUpgrade
        static void setSourceCompatibility(JavaPluginExtension extension, Object sourceCompatibility) {
            extension.getSourceCompatibility().set(JavaVersion.toVersion(sourceCompatibility));
        }
    }

    static class TargetCompatibilityAdapter {
        @BytecodeUpgrade
        static JavaVersion getTargetCompatibility(JavaPluginExtension extension) {
            return extension.getTargetCompatibility().get();
        }

        @BytecodeUpgrade
        static void setTargetCompatibility(JavaPluginExtension extension, JavaVersion targetCompatibility) {
            extension.getTargetCompatibility().set(targetCompatibility);
        }

        @BytecodeUpgrade
        static void setTargetCompatibility(JavaPluginExtension extension, Object targetCompatibility) {
            extension.getTargetCompatibility().set(JavaVersion.toVersion(targetCompatibility));
        }
    }


    static class AutoTargetJvmDisabledAdapter {

        @BytecodeUpgrade
        static boolean getAutoTargetJvmDisabled(JavaPluginExtension extension) {
            return !extension.getAutoTargetJvm().get();
        }
    }
}
