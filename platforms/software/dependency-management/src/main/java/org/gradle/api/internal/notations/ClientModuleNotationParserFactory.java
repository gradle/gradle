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
package org.gradle.api.internal.notations;

import com.google.common.collect.Interner;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.jspecify.annotations.NonNull;

@SuppressWarnings("deprecation")
public class ClientModuleNotationParserFactory implements Factory<NotationParser<Object, org.gradle.api.artifacts.ClientModule>> {

    private final Instantiator instantiator;
    private final Interner<String> stringInterner;

    public ClientModuleNotationParserFactory(Instantiator instantiator, Interner<String> stringInterner) {
        this.instantiator = instantiator;
        this.stringInterner = stringInterner;
    }

    @NonNull
    @Override
    public NotationParser<Object, org.gradle.api.artifacts.ClientModule> create() {
        return NotationParserBuilder.toType(org.gradle.api.artifacts.ClientModule.class)
                .fromCharSequence(new DependencyStringNotationConverter<>(instantiator, DefaultClientModule.class, stringInterner))
                .converter(new DependencyMapNotationConverter<>(instantiator, DefaultClientModule.class))
                .toComposite();

    }
}
