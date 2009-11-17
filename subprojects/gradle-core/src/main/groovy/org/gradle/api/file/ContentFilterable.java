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
package org.gradle.api.file;

import groovy.lang.Closure;

import java.io.FilterReader;
import java.util.Map;

public interface ContentFilterable {
    /**
     * Adds a content filter to be used during the copy.  Multiple calls to filter, add additional filters to the filter
     * chain.  Each filter should implement java.io.FilterReader. Include org.apache.tools.ant.filters.* for access to
     * all the standard ANT filters. <p> Filter parameters may be specified using groovy map syntax. <p> Examples:
     * <pre>
     *    filter(HeadFilter, lines:25, skip:2)
     *    filter(ReplaceTokens, tokens:[copyright:'2009', version:'2.3.1'])
     * </pre>
     *
     * @param map map of filter parameters
     * @param filterType Class of filter to add
     * @return this
     */
    ContentFilterable filter(Map<String, ?> map, Class<? extends FilterReader> filterType);

    /**
     * Adds a content filter to be used during the copy.  Multiple calls to filter, add additional filters to the filter
     * chain.  Each filter should implement java.io.FilterReader. Include org.apache.tools.ant.filters.* for access to
     * all the standard ANT filters. <p> Examples:
     * <pre>
     *    filter(StripJavaComments)
     *    filter(com.mycompany.project.CustomFilter)
     * </pre>
     *
     * @param filterType Class of filter to add
     * @return this
     */
    ContentFilterable filter(Class<? extends FilterReader> filterType);

    /**
     * Adds a content filter based on the provided closure.  The Closure will be called with each line (stripped of line
     * endings) and should return a String to replace the line.
     *
     * @param closure to implement line based filtering
     * @return this
     */
    ContentFilterable filter(Closure closure);
}
