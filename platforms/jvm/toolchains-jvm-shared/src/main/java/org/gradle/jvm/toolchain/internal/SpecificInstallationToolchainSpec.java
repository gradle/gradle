/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.provider.PropertyFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Objects;

public class SpecificInstallationToolchainSpec extends DefaultToolchainSpec {

    public static class Key implements JavaToolchainSpecInternal.Key {

        private final File javaHome;

        public Key(File javaHome) {
            this.javaHome = javaHome;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return Objects.equals(javaHome, key.javaHome);
        }

        @Override
        public int hashCode() {
            return Objects.hash(javaHome);
        }
    }

    private final File javaHome;

    @Inject
    public SpecificInstallationToolchainSpec(PropertyFactory propertyFactory, File javaHome) {
        super(propertyFactory);
        this.javaHome = javaHome;

        // disallow changing property values
        finalizeProperties();
    }

    public static SpecificInstallationToolchainSpec fromJavaHome(PropertyFactory propertyFactory, File javaHome) {
        if (javaHome.exists()) {
            if (javaHome.isDirectory()) {
                return new SpecificInstallationToolchainSpec(propertyFactory, javaHome);
            } else {
                throw new InvalidUserDataException("The configured Java home is not a directory (" + javaHome.getAbsolutePath() + ")");
            }
        } else {
            throw new InvalidUserDataException("The configured Java home does not exist (" + javaHome.getAbsolutePath() + ")");
        }
    }

    public static SpecificInstallationToolchainSpec fromJavaExecutable(PropertyFactory propertyFactory, String executable) {
        return new SpecificInstallationToolchainSpec(propertyFactory, JavaExecutableUtils.resolveJavaHomeOfExecutable(executable));
    }

    @Override
    public Key toKey() {
        return new Key(javaHome);
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
        return javaHome;
    }

    @Override
    public String getDisplayName() {
        return MoreObjects.toStringHelper("SpecificToolchain ").add("javaHome", javaHome).toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
