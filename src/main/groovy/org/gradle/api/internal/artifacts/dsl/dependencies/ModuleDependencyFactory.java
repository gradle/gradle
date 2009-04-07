/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.internal.artifacts.dsl.dependencies.IDependencyImplementationFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.artifacts.DependencyArtifact;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hans Dockter
 */
public class ModuleDependencyFactory implements IDependencyImplementationFactory {
    private StringNotationParser stringNotationParser = new StringNotationParser();

    public DefaultModuleDependency createDependency(Object notation) {
        assert notation != null;
        if (notation instanceof String || notation instanceof GString) {
            return stringNotationParser.createDependency(notation.toString());
        }
        throw new UnknownDependencyNotation();
    }

    private static class MapNotationParser {

    }

    private static class StringNotationParser {
        private static final Pattern extensionSplitter = Pattern.compile("^(.+)\\@([^:]+$)");
        
        public DefaultModuleDependency createDependency(String notation) {
            ParsedModuleStringNotation parsedNotation = splitDescriptionIntoModuleNotationAndArtifactType(notation);
            DefaultModuleDependency moduleDependency = new DefaultModuleDependency(
                    parsedNotation.getGroup(),
                    parsedNotation.getName(),
                    parsedNotation.getVersion());
            String actualArtifactType = parsedNotation.getArtifactType();
            if (parsedNotation.getArtifactType() == null) {
                if (parsedNotation.getClassifier() != null) {
                    actualArtifactType = DependencyArtifact.DEFAULT_TYPE;
                }
            } else {
                moduleDependency.setTransitive(false);
            }
            if (actualArtifactType != null) {
                moduleDependency.addArtifact(new DefaultDependencyArtifact(moduleDependency.getName(),
                        actualArtifactType, actualArtifactType, parsedNotation.getClassifier(), null));
            }
            return moduleDependency;
        }

        public ParsedModuleStringNotation splitDescriptionIntoModuleNotationAndArtifactType(String notation) {
            Matcher matcher = extensionSplitter.matcher(notation);
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
