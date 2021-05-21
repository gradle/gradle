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

import org.gradle.internal.jvm.inspection.JvmVendor;

import java.io.File;
import java.util.Comparator;

public class JavaToolchainComparator implements Comparator<JavaToolchain> {

    @Override
    public int compare(JavaToolchain o1, JavaToolchain o2) {
        return Comparator
            .comparing(JavaToolchain::isJdk)
            .thenComparing(this::extractVendor, Comparator.reverseOrder())
            .thenComparing(JavaToolchain::getToolVersion)
            // It is possible for different JDK builds to have exact same version. The input order
            // may change so the installation path breaks ties to keep sorted output consistent
            // between runs.
            .thenComparing(this::extractInstallationPathAsFile)
            .reversed()
            .compare(o1, o2);
    }

    private JvmVendor.KnownJvmVendor extractVendor(JavaToolchain toolchain) {
        return toolchain.getMetadata().getVendor().getKnownVendor();
    }

    private File extractInstallationPathAsFile(JavaToolchain javaToolchain) {
        return javaToolchain.getInstallationPath().getAsFile();
    }
}
