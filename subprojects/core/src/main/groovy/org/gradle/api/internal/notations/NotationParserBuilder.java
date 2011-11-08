/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.notations;

/**
 * by Szczepan Faber, created at: 11/8/11
 */
public class NotationParserBuilder {
    private Class resultingType;
    private StringNotationParser stringNotationParser;
    private MapNotationParser mapNotationParser;
    private String invalidNotationMessage;

    public NotationParserBuilder resultingType(Class resultingType) {
        this.resultingType = resultingType;
        return this;
    }

    public NotationParserBuilder stringParser(StringNotationParser stringNotationParser) {
        this.stringNotationParser = stringNotationParser;
        return this;
    }

    public NotationParserBuilder mapParser(MapNotationParser mapNotationParser) {
        this.mapNotationParser = mapNotationParser;
        return this;
    }

    public NotationParserBuilder invalidNotationMessage(String invalidNotationMessage) {
        this.invalidNotationMessage = invalidNotationMessage;
        return this;
    }

    public DefaultNotationParser build() {
        return new DefaultNotationParser(new JustReturningParser(resultingType),
                stringNotationParser,
                mapNotationParser,
                new AlwaysThrowingParser(invalidNotationMessage));
    }
}
