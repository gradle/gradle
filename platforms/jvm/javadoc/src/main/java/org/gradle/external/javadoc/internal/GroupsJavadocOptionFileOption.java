/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.external.javadoc.internal;

import com.google.common.collect.Lists;
import org.gradle.util.internal.CollectionUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link org.gradle.external.javadoc.JavadocOptionFileOption} which represents the -groups command line
 * option.
 */
public class GroupsJavadocOptionFileOption extends AbstractJavadocOptionFileOption<Map<String, List<String>>> {
    public GroupsJavadocOptionFileOption(String option, Map<String, List<String>> value) {
        super(option, value);
    }

    @Override
    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if (value != null && !value.isEmpty()) {
            for (final String group : value.keySet()) {
                final List<String> groupPackages = value.get(group);

                writerContext
                    .writeOptionHeader(option)
                    .write("\"" + group + "\"")
                    .write(" ")
                    .write("\"" + CollectionUtils.join(":", groupPackages) + "\"")
                    .newLine();
            }
        }
    }

    @Override
    public GroupsJavadocOptionFileOption duplicate() {
        Map<String, List<String>> duplicateValue = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : value.entrySet()) {
            duplicateValue.put(entry.getKey(), Lists.newArrayList(entry.getValue()));
        }
        return new GroupsJavadocOptionFileOption(option, duplicateValue);
    }
}
