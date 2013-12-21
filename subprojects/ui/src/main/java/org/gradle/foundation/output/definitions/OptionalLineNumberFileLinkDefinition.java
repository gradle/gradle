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
package org.gradle.foundation.output.definitions;

/**
 * This is just like BasicFileLinkDefinition except that the line number delimiter is optional and it allows spaces between the file and the delimiter.
 */
public class OptionalLineNumberFileLinkDefinition extends PrefixedFileLinkDefinition {
    public OptionalLineNumberFileLinkDefinition(String name, String prefix, String extension, String lineNumberDelimiter) {
        super(name, prefix, extension, lineNumberDelimiter);
    }

    /**
     * This has been overridden to optionally look for a line number delimiter.
     */
    @Override
    protected String generateLineNumberExpression(String lineNumberDelimiter) {
        return "(.*" + quoteLiteral(lineNumberDelimiter) + ".*\\d*)?";
    }
}
