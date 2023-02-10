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
import org.gradle.api.model.ObjectFactory;

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

    private SpecificInstallationToolchainSpec(ObjectFactory factory, File javaHome) {
        super(factory);
        this.javaHome = javaHome;

        // disallow changing property values
        finalizeProperties();
    }

    public static SpecificInstallationToolchainSpec fromJavaHome(ObjectFactory objectFactory, File javaHome) {
        if (javaHome.exists()) {
            if (javaHome.isDirectory()) {
                return new SpecificInstallationToolchainSpec(objectFactory, javaHome);
            } else {
                throw new InvalidUserDataException("The configured Java home is not a directory (" + javaHome.getAbsolutePath() + ")");
            }
        } else {
            throw new InvalidUserDataException("The configured Java home does not exist (" + javaHome.getAbsolutePath() + ")");
        }
    }

    public static SpecificInstallationToolchainSpec fromJavaExecutable(ObjectFactory objectFactory, String executable) {
        File executableFile = JavaExecutableUtils.resolveExecutable(executable);
        // Relying on the layout of the toolchain distribution: <JAVA HOME>/bin/<executable>
        final File parentJavaHome = executableFile.getParentFile().getParentFile();
        return new SpecificInstallationToolchainSpec(objectFactory, parentJavaHome);
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
        return MoreObjects.toStringHelper("SpecificToolchain").add("javaHome", javaHome).toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
