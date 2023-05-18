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

package org.gradle.internal.jvm.inspection;

import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.internal.InstallationLocation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class CachingJvmMetadataDetector implements JvmMetadataDetector, ConditionalInvalidation<JvmInstallationMetadata> {

    private final Map<File, JvmInstallationMetadata> javaMetadata = Collections.synchronizedMap(new HashMap<>());
    private final JvmMetadataDetector delegate;

    public CachingJvmMetadataDetector(JvmMetadataDetector delegate) {
        this.delegate = delegate;
        getMetadata(new InstallationLocation(Jvm.current().getJavaHome(), "current Java home"));
    }

    @Override
    public JvmInstallationMetadata getMetadata(InstallationLocation javaInstallationLocation) {
        File javaHome = resolveSymlink(javaInstallationLocation.getLocation());
        return javaMetadata.computeIfAbsent(javaHome, file -> delegate.getMetadata(javaInstallationLocation));
    }

    private File resolveSymlink(File jdkPath) {
        try {
            return jdkPath.getCanonicalFile();
        } catch (IOException e) {
            return jdkPath;
        }
    }

    @Override
    public void invalidateItemsMatching(Predicate<JvmInstallationMetadata> predicate) {
        synchronized (javaMetadata) {
            javaMetadata.entrySet().removeIf(it -> predicate.test(it.getValue()));
        }
    }
}
