/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cacheable;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSortedSet;
import org.apache.commons.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;

public class DependenciesFile {

    public static void write(Collection<String> dependencies, File outputFile) throws IOException {
        BufferedWriter outputStreamWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), Charsets.UTF_8));
        try {
            for (String discoveredInput : ImmutableSortedSet.copyOf(dependencies)) {
                outputStreamWriter.write(discoveredInput);
                outputStreamWriter.newLine();
            }
        } finally {
            IOUtils.closeQuietly(outputStreamWriter);
        }
    }
}
