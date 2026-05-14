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
package org.gradle.api.internal.artifacts.repositories.mirror;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

/**
 * A reference to an external value source for a mirror credential. Literal values are
 * intentionally not supported in v1; users must point at an environment variable,
 * system property, or Gradle property to avoid storing secrets in the TOML file.
 */
public final class MirrorValueReference {

    public enum Kind {
        ENV("env"),
        SYS("sys"),
        GRADLE_PROPERTY("gradleProperty");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    private final Kind kind;
    private final String name;

    public MirrorValueReference(Kind kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public Provider<String> resolve(ProviderFactory providerFactory) {
        switch (kind) {
            case ENV:
                return providerFactory.environmentVariable(name);
            case SYS:
                return providerFactory.systemProperty(name);
            case GRADLE_PROPERTY:
                return providerFactory.gradleProperty(name);
            default:
                throw new IllegalStateException("Unknown reference kind: " + kind);
        }
    }

    @Override
    public String toString() {
        return "${" + kind.prefix + "." + name + "}";
    }
}
