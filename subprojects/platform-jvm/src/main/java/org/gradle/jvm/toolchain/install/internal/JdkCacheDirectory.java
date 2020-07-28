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

package org.gradle.jvm.toolchain.install.internal;

import org.gradle.api.internal.file.FileOperations;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class JdkCacheDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkCacheDirectory.class);

    private final GradleUserHomeDirProvider homeDirProvider;
    private final FileOperations operations;

    public JdkCacheDirectory(GradleUserHomeDirProvider homeDirProvider, FileOperations operations) {
        this.homeDirProvider = homeDirProvider;
        this.operations = operations;
    }

    public Set<File> listJavaHomes() {
        final File[] files = getJdksDirectory().listFiles();
        if (files != null) {
            return Arrays.stream(files).filter(File::isDirectory).map(this::findJavaHome).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private File findJavaHome(File potentialHome) {
        if (new File(potentialHome, "Contents/Home").exists()) {
            return new File(potentialHome, "Contents/Home");
        }
        return potentialHome;
    }

    // TODO: [bm] any reason to use CacheScopeMapping instead?
    private File getJdksDirectory() {
        return new File(homeDirProvider.getGradleUserHomeDirectory(), "jdks");
    }

}
