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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class ModuleDependencyFactory implements IDependencyImplementationFactory {
    private StringNotationParser stringNotationParser = new StringNotationParser();
    private MapModuleNotationParser mapNotationParser = new MapModuleNotationParser();

    public DefaultExternalModuleDependency createDependency(Object notation) {
        assert notation != null;
        if (notation instanceof String || notation instanceof GString) {
            return stringNotationParser.createDependency(notation.toString());
        } else if (notation instanceof Map) {
            return (DefaultExternalModuleDependency) mapNotationParser.createDependency(DefaultExternalModuleDependency.class, (Map) notation);
        }
        throw new IllegalDependencyNotation();
    }

    private static class StringNotationParser {
        private static final Pattern EXTENSION_SPLITTER = Pattern.compile("^(.+)\\@([^:]+$)");

        public DefaultExternalModuleDependency createDependency(String notation) {
            ParsedModuleStringNotation parsedNotation = splitDescriptionIntoModuleNotationAndArtifactType(notation);
            DefaultExternalModuleDependency moduleDependency = new DefaultExternalModuleDependency(
                    parsedNotation.getGroup(),
                    parsedNotation.getName(),
                    parsedNotation.getVersion());
            ModuleFactoryHelper.addExplicitArtifactsIfDefined(moduleDependency, parsedNotation.getArtifactType(), parsedNotation.getClassifier());
            return moduleDependency;
        }

        public ParsedModuleStringNotation splitDescriptionIntoModuleNotationAndArtifactType(String notation) {
            Matcher matcher = EXTENSION_SPLITTER.matcher(notation);
            boolean hasArtifactType = matcher.matches();
            if (hasArtifactType) {
                if (matcher.groupCount() != 2) {
                    throw new InvalidUserDataException("The description " + notation + " is invalid");
                }
                return new ParsedModuleStringNotation(matcher.group(1), matcher.group(2));
            }
            return new ParsedModuleStringNotation(notation, null);
        }

    }

}
