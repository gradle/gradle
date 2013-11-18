/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.typeconversion;

import java.util.Collection;
import java.util.LinkedList;

public class FromStringNotationParserFactory<T> {

    private Collection<NotationParser<? extends T>> notationParsers = new LinkedList<NotationParser<? extends T>>();
    private TypeInfo<T> resultingType;

    public FromStringNotationParserFactory<T> resultingType(TypeInfo<T> resultingType) {
        this.resultingType = resultingType;
        return this;
    }

    public FromStringNotationParserFactory(Class<T> optionType) {
        this.resultingType(new TypeInfo<T>(optionType));
    }

    public NotationParser<T> toComposite() {
        return create();
    }

    private CompositeNotationParser<T> create() {
        assert resultingType != null : "resultingType cannot be null";
        if(resultingType.getTargetType().isEnum()){
            notationParsers.add(new EnumFromStringNotationParser<T>(resultingType.getTargetType()));
        }else{
            notationParsers.add(new JustReturningParser<T>(resultingType.getTargetType()){
                public void describe(Collection<String> candidateFormats) {
                }
            });
        }
        return new CompositeNotationParser<T>(notationParsers);
    }
}
