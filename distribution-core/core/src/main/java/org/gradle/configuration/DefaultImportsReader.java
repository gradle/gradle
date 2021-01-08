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

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class DefaultImportsReader implements ImportsReader {

    private static final String RESOURCE = "/default-imports.txt";
    private static final String MAPPING_RESOURCE = "/api-mapping.txt";
    private final String[] importPackages;
    private final Map<String, List<String>> simpleNameToFQCN;

    public DefaultImportsReader() {
        try {
            this.importPackages = generateImportPackages();
            this.simpleNameToFQCN = generateSimpleNameToFQCN();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String[] getImportPackages() {
        return importPackages;
    }

    @Override
    public Map<String, List<String>> getSimpleNameToFullClassNamesMapping() {
        return simpleNameToFQCN;
    }

    /**
     * @implNote Logic is duplicated in {@link gradlebuild.integrationtests.action.AnnotationGeneratorWorkAction}.
     * Please keep this code in sync.
     */
    private static String[] generateImportPackages() throws IOException {
        URL url = DefaultImportsReader.class.getResource(RESOURCE);
        if (url == null) {
            throw new IllegalStateException("Could not load default imports resource: " + RESOURCE);
        }
        return Resources.asCharSource(url, Charsets.UTF_8).readLines(new LineProcessor<String[]>() {
            private final List<String> packages = Lists.newLinkedList();

            @Override
            public boolean processLine(@SuppressWarnings("NullableProblems") String line) throws IOException {
                packages.add(line.substring(7, line.length() - 2));
                return true;
            }

            @Override
            public String[] getResult() {
                return packages.toArray(new String[packages.size()]);
            }
        });
    }

    private static Map<String, List<String>> generateSimpleNameToFQCN() throws IOException {
        URL url = DefaultImportsReader.class.getResource(MAPPING_RESOURCE);
        if (url == null) {
            throw new IllegalStateException("Could not load default imports resource: " + MAPPING_RESOURCE);
        }
        return Resources.asCharSource(url, Charsets.UTF_8).readLines(new LineProcessor<Map<String, List<String>>>() {
            private final ImmutableMap.Builder<String, List<String>> builder = ImmutableMap.builder();

            @Override
            public boolean processLine(String line) throws IOException {
                boolean process = !StringUtils.isEmpty(line);
                if (process) {
                    String[] split = line.split(":");
                    if (split.length==2) {
                        String simpleName = split[0];
                        List<String> fqcns = Splitter.on(';').omitEmptyStrings().splitToList(split[1]);
                        builder.put(simpleName, fqcns);
                    } else {
                        process = false;
                    }
                }
                return process;
            }

            @Override
            public Map<String, List<String>> getResult() {
                return builder.build();
            }
        });
    }
}
