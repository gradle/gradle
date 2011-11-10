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

import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.api.internal.artifacts.dsl.dependencies.ParsedModuleStringNotation;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;

/**
 * by Szczepan Faber, created at: 11/10/11
 */
public class ClientDependencyStringNotationParser extends TypedNotationParser<CharSequence, ClientModule> {

    private final Instantiator instantiator;

    public ClientDependencyStringNotationParser(Instantiator instantiator) {
        super(CharSequence.class);
        this.instantiator = instantiator;
    }

    protected ClientModule parseType(CharSequence notation) {
        ParsedModuleStringNotation parsedNotation = new ParsedModuleStringNotation(notation.toString(), null);
        DefaultClientModule clientModule = instantiator.newInstance(DefaultClientModule.class,
                parsedNotation.getGroup(), parsedNotation.getName(), parsedNotation.getVersion());
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(clientModule, null, parsedNotation.getClassifier());
        return clientModule;
    }
}
