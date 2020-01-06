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
import org.gradle.api.Transformer;

import java.io.FilterReader;
import java.util.Map;

/**
 * Represents some binary resource whose content can be filtered.
 */
public interface ContentFilterable {
    /**
     * <p>Adds a content filter to be used during the copy.  Multiple calls to filter, add additional filters to the
     * filter chain.  Each filter should implement {@code java.io.FilterReader}. Include {@code
     * org.apache.tools.ant.filters.*} for access to all the standard Ant filters.</p>
     *
     * <p>Filter properties may be specified using groovy map syntax.</p>
     *
     * <p> Examples:
     * <pre>
     *    filter(HeadFilter, lines:25, skip:2)
     *    filter(ReplaceTokens, tokens:[copyright:'2009', version:'2.3.1'])
     * </pre>
     *
     * @param properties map of filter properties
     * @param filterType Class of filter to add
     * @return this
     */
    ContentFilterable filter(Map<String, ?> properties, Class<? extends FilterReader> filterType);

    /**
     * <p>Adds a content filter to be used during the copy.  Multiple calls to filter, add additional filters to the
     * filter chain.  Each filter should implement {@code java.io.FilterReader}. Include {@code
     * org.apache.tools.ant.filters.*} for access to all the standard Ant filters.</p>
     *
     * <p> Examples:
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
     * endings) and should return a String to replace the line or {@code null} to remove the line.  If every line is
     * removed, the result will be an empty file, not an absent one.
     *
     * @param closure to implement line based filtering
     * @return this
     */
    ContentFilterable filter(Closure closure);

    /**
     * Adds a content filter based on the provided transformer.  The Closure will be called with each line (stripped of line
     * endings) and should return a String to replace the line or {@code null} to remove the line.  If every line is
     * removed, the result will be an empty file, not an absent one.
     *
     * @param transformer to implement line based filtering
     * @return this
     */
    ContentFilterable filter(Transformer<String, String> transformer);

    /**
     * <p>Expands property references in each file as it is copied. More specifically, each file is transformed using
     * Groovy's {@link groovy.text.SimpleTemplateEngine}. This means you can use simple property references, such as
     * <code>$property</code> or <code>${property}</code> in the file. You can also include arbitrary Groovy code in the
     * file, such as <code>${version ?: 'unknown'}</code> or <code>${classpath*.name.join(' ')}</code>
     *
     * @param properties to implement line based filtering
     * @return this
     */
    ContentFilterable expand(Map<String, ?> properties);
}
