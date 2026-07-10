/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.base.MoreObjects;
import org.gradle.api.internal.provider.PropertyFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Objects;

/**
 * Represents a request for a Java toolchain using a specific 'java' executable.  The resulting toolchain
 * will only be able to provide that executable, and no other tools.
 */
public class SpecificExecutableToolchainSpec extends DefaultToolchainSpec {
    private final File javaExecutable;

    public static class Key implements JavaToolchainSpecInternal.Key {

        private final File javaExecutable;

        public Key(File javaExecutable) {
            this.javaExecutable = javaExecutable;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SpecificExecutableToolchainSpec.Key key = (SpecificExecutableToolchainSpec.Key) o;
            return Objects.equals(javaExecutable, key.javaExecutable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(javaExecutable);
        }
    }

    @Inject
    public SpecificExecutableToolchainSpec(PropertyFactory propertyFactory, File javaExecutable) {
        super(propertyFactory);
        this.javaExecutable = javaExecutable;
    }

    @Override
    public SpecificInstallationToolchainSpec.Key toKey() {
        return new SpecificInstallationToolchainSpec.Key(javaExecutable);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public File getJavaHome() {
        // This allows for a "normal" JDK layout where the 'java' executable is in JAVA_HOME/bin/java.
        // If such an executable is provided, and Gradle can probe the executable, the resulting toolchain
        // could potentially be used to provide other tools as well (e.g. a compiler or javadoc tool).
        return javaExecutable.getParentFile().getParentFile();
    }

    @Override
    public String getDisplayName() {
        return MoreObjects.toStringHelper("SpecificToolchain ").add("javaExecutable", javaExecutable).toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public File getJavaExecutable() {
        return javaExecutable;
    }
}
