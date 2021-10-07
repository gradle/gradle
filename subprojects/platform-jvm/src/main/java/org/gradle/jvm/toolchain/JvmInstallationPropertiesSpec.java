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

package org.gradle.jvm.toolchain;

import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.jvm.toolchain.internal.JavaToolchain;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class JvmInstallationPropertiesSpec implements Predicate<JavaToolchain> {

    private final Map<JvmInstallationProperty, Predicate<String>> matchers = new HashMap<>();

    public void property(JvmInstallationProperty key, Predicate<String> matcher) {
        matchers.put(key, matcher); //TODO: key already present?
    }

    public Predicate<String> contains(String text) {
        return s -> s.contains(text);
    }

    @Override
    public boolean test(JavaToolchain toolchain) {
        JvmInstallationMetadata metadata = toolchain.getMetadata();
        return matchers.entrySet().stream()
            .allMatch(entry -> entry.getValue().test(getValueFor(entry.getKey(), metadata)));
    }

    private String getValueFor(JvmInstallationProperty property, JvmInstallationMetadata metadata) {
        switch (property) {
            case ARCH:
                return "oops"; //todo
            case VM_VERSION:
                return metadata.getJvmVersion();
            default:
                throw new RuntimeException("Ooosp"); //todo
        }
    }

    public enum JvmInstallationProperty {
        ARCH,
        VM_VERSION
    }

}
