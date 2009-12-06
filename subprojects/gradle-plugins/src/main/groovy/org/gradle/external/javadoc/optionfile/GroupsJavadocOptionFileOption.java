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

package org.gradle.external.javadoc.optionfile;

import org.gradle.util.GUtil;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class GroupsJavadocOptionFileOption extends AbstractJavadocOptionFileOption<Map<String, List<String>>> {
    public GroupsJavadocOptionFileOption(String option) {
        super(option, new HashMap<String, List<String>>());
    }

    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if ( value != null && !value.isEmpty() ) {
            for ( final String group : value.keySet() ) {
                final List<String> groupPackages = value.get(group);

                writerContext
                    .writeOptionHeader(option)
                    .write(
                        new StringBuffer()
                            .append("\"")
                            .append(group)
                            .append("\"")
                            .toString())
                    .write(" ")
                    .write(
                        new StringBuffer()
                            .append("\"")
                            .append(GUtil.join(groupPackages, ":"))
                            .append("\"")
                            .toString())
                    .newLine();
            }
        }
    }
}
