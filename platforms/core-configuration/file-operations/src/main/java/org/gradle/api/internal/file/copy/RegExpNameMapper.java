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
package org.gradle.api.internal.file.copy;

import org.gradle.api.Transformer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegExpNameMapper implements Transformer<String, String> {
    private final Pattern pattern;
    private transient Matcher matcher;
    private final String replacement;

    public RegExpNameMapper(String sourceRegEx, String replaceWith) {
        this(Pattern.compile(sourceRegEx), replaceWith);
    }

    public RegExpNameMapper(Pattern sourceRegEx, String replaceWith) {
        pattern = sourceRegEx;
        replacement = replaceWith;
    }

    @Override
    public String transform(String source) {
        if (matcher == null) {
            matcher = pattern.matcher(source);
        } else {
            matcher.reset(source);
        }
        String result = source;
        if (matcher.find()) {
            result = matcher.replaceFirst(replacement);
        }
        return result;
    }
}
