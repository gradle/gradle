/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.configuration;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.UncheckedException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class DefaultImportsReader implements ImportsReader {

    private String importsText;

    public String getImports() {
        if (importsText == null) {
            try {
                URL url = getClass().getResource("/default-imports.txt");
                InputStreamReader reader = new InputStreamReader(url.openStream(), "UTF8");
                try {
                    int bufferSize = 8192; // at time of writing, the file was about 7k so this should cover in one read
                    StringBuilder imports = new StringBuilder(bufferSize);
                    char[] chars = new char[bufferSize];

                    int numRead = reader.read(chars, 0, bufferSize);
                    while (numRead != -1) {
                        imports.append(chars, 0, numRead);
                        numRead = reader.read(chars, 0, bufferSize);
                    }

                    importsText = imports.toString();
                } finally {
                    reader.close();
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        return importsText;
    }

    @Override
    public List<String> getImportPackages() {
        String[] lines = getImports().split("\\r?\\n");
        return ImmutableList.copyOf(
                FluentIterable.from(Arrays.asList(lines)).filter(new Predicate<String>() {
                    public boolean apply(String input) {
                        return !input.isEmpty();
                    }
                }).transform(new Function<String, String>() {
                    public String apply(String input) {
                        return input.substring(7, input.length() - 1);
                    }
                })
        );
    }
}
