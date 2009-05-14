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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.GString;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultClientModuleFactory implements ClientModuleFactory {
    private StringNotationParser stringNotationParser = new StringNotationParser();
    private MapModuleNotationParser mapNotationParser = new MapModuleNotationParser();

    public ClientModule createClientModule(Object notation) {
        assert notation != null;
        if (notation instanceof String || notation instanceof GString) {
            return stringNotationParser.createDependency(notation.toString());
        } else if (notation instanceof Map) {
            return (ClientModule) mapNotationParser.createDependency(DefaultClientModule.class, (Map) notation);
        }
        throw new IllegalDependencyNotation();
    }

    private static class StringNotationParser {
        public DefaultClientModule createDependency(String notation) {
            ParsedModuleStringNotation parsedNotation = new ParsedModuleStringNotation(notation, null);
            DefaultClientModule clientModule = new DefaultClientModule(
                    parsedNotation.getGroup(),
                    parsedNotation.getName(),
                    parsedNotation.getVersion());
            ModuleFactoryHelper.addExplicitArtifactsIfDefined(clientModule, null, parsedNotation.getClassifier());
            return clientModule;
        }
    }
}
