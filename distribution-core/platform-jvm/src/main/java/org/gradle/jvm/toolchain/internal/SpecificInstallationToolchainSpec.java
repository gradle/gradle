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
import com.google.common.base.Objects;
import org.gradle.api.model.ObjectFactory;

import java.io.File;

public class SpecificInstallationToolchainSpec extends DefaultToolchainSpec {

    private final File javaHome;

    public SpecificInstallationToolchainSpec(ObjectFactory factory, File javaHome) {
        super(factory);
        this.javaHome = javaHome;
    }

    @Override
    public boolean isConfigured() {
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SpecificInstallationToolchainSpec that = (SpecificInstallationToolchainSpec) o;
        return Objects.equal(javaHome, that.javaHome);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(javaHome);
    }
}
