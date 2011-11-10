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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.api.internal.artifacts.dsl.dependencies.ParsedModuleStringNotation;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * by Szczepan Faber, created at: 11/10/11
 */
public class DependencyStringNotationParser extends TypedNotationParser<CharSequence, ExternalModuleDependency> {

    private final Instantiator instantiator;

    public DependencyStringNotationParser(Instantiator instantiator) {
        super(CharSequence.class);
        this.instantiator = instantiator;
    }

    protected ExternalModuleDependency parseType(CharSequence notation) {
        return createDependencyFromString(notation.toString());
    }

    private static final Pattern EXTENSION_SPLITTER = Pattern.compile("^(.+)\\@([^:]+$)");

    private DefaultExternalModuleDependency createDependencyFromString(String notation) {
        ParsedModuleStringNotation parsedNotation = splitDescriptionIntoModuleNotationAndArtifactType(notation);
        DefaultExternalModuleDependency moduleDependency = instantiator.newInstance(
                DefaultExternalModuleDependency.class, parsedNotation.getGroup(), parsedNotation.getName(),
                parsedNotation.getVersion());
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(moduleDependency, parsedNotation.getArtifactType(),
                parsedNotation.getClassifier());
        return moduleDependency;
    }

    private ParsedModuleStringNotation splitDescriptionIntoModuleNotationAndArtifactType(String notation) {
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
