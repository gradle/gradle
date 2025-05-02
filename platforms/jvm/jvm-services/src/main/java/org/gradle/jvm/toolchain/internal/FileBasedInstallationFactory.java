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

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class FileBasedInstallationFactory {

    public static Set<InstallationLocation> fromDirectory(File rootDirectory, String supplierName, BiFunction<File, String, InstallationLocation> locationFactory) {
        final File[] javaCandidates = rootDirectory.listFiles();
        if (javaCandidates == null) {
            return Collections.emptySet();
        }
        return Stream.of(javaCandidates)
            .filter(File::isDirectory)
            .map(d -> locationFactory.apply(d, supplierName))
            .collect(toSet());
    }

}
