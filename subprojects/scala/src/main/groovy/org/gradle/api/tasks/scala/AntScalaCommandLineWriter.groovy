/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.gradle.api.Transformer
import org.gradle.util.CollectionUtils

import java.util.regex.Pattern

class AntScalaCommandLineWriter {

    static String toCommandLineArg(List<String> arguments) {
        arguments.isEmpty() ? " " : CollectionUtils.collect(arguments, toEscapedString).join(' ')
    }

    static String toCommaSeparatedArg(List<String> arguments) {
        arguments.isEmpty() ? " " : arguments.join(',')
    }

    // there appears to be a bug in the groovy compiler that prevents these being local to the Transformer
    private static def singleQuoted = Pattern.compile("^'.*'\$")
    private static def doubleQuoted = Pattern.compile("^\".*\"\$")
    private static def aSingleQuote = Pattern.compile("'")

    private static def toEscapedString = new Transformer<String, String>() {

        @Override
        String transform(String input) {
            if (singleQuoted.matcher(input).matches() || doubleQuoted.matcher(input).matches() || !input.contains(' ')) {
                input
            } else {
                wrapWithSingleQuotes(input)
            }
        }

        def wrapWithSingleQuotes(String input) {
            String.format("'%1\$s'", escapeSingleQuotes(input))
        }

        def escapeSingleQuotes(String input) {
            aSingleQuote.matcher(input).replaceAll("\\\\'")
        }
    }
}
