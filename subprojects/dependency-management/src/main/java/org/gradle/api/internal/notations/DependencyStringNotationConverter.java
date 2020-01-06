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

import com.google.common.collect.Interner;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.ParsedModuleStringNotation;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.TypeConversionException;

public class DependencyStringNotationConverter<T> implements NotationConverter<String, T> {
    private final Instantiator instantiator;
    private final Class<T> wantedType;
    private final Interner<String> stringInterner;

    public DependencyStringNotationConverter(Instantiator instantiator, Class<T> wantedType, Interner<String> stringInterner) {
        this.instantiator = instantiator;
        this.wantedType = wantedType;
        this.stringInterner = stringInterner;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("String or CharSequence values").example("'org.gradle:gradle-core:1.0'");
    }

    @Override
    public void convert(String notation, NotationConvertResult<? super T> result) throws TypeConversionException {
        result.converted(createDependencyFromString(notation));
    }

    private T createDependencyFromString(String notation) {

        ParsedModuleStringNotation parsedNotation = splitModuleFromExtension(notation);
        RichVersion version = parseStrict(parsedNotation.getVersion());
        T moduleDependency = instantiator.newInstance(wantedType,
            stringInterner.intern(parsedNotation.getGroup()), stringInterner.intern(parsedNotation.getName()), stringInterner.intern(version.require));
        maybeEnrichVersion(version, moduleDependency);
        if (moduleDependency instanceof ExternalDependency) {
            ModuleFactoryHelper.addExplicitArtifactsIfDefined((ExternalDependency) moduleDependency, parsedNotation.getArtifactType(), parsedNotation.getClassifier());
        }

        return moduleDependency;
    }

    private void maybeEnrichVersion(DependencyStringNotationConverter.RichVersion version, T moduleDependency) {
        if (version.strictly != null) {
            Action<MutableVersionConstraint> versionAction = v -> {
                v.strictly(version.strictly);
                if (!version.prefer.isEmpty()) {
                    v.prefer(version.prefer);
                }
            };
            if (moduleDependency instanceof ExternalDependency) {
                ((ExternalDependency) moduleDependency).version(versionAction);
            }
            if (moduleDependency instanceof DependencyConstraint) {
                ((DependencyConstraint) moduleDependency).version(versionAction);
            }
        }
    }

    private RichVersion parseStrict(String version) {
        if (version == null) {
            return RichVersion.EMPTY;
        }
        int idx = version.indexOf("!!");
        if (idx == 0) {
            throw new InvalidUserCodeException("The strict version modifier (!!) must be appended to a valid version number");
        }
        if (idx > 0) {
            String strictly = stringInterner.intern(version.substring(0, idx));
            String prefer = stringInterner.intern(version.substring(idx+2));
            return new RichVersion(null, strictly, prefer);
        }
        return new RichVersion(stringInterner.intern(version), null, null);
    }

    private ParsedModuleStringNotation splitModuleFromExtension(String notation) {
        int idx = notation.lastIndexOf('@');
        if (idx == -1 || ClientModule.class.isAssignableFrom(wantedType)) {
            return new ParsedModuleStringNotation(notation, null);
        }
        int versionIndx = notation.lastIndexOf(':');
        if (versionIndx < idx) {
            return new ParsedModuleStringNotation(notation.substring(0, idx), notation.substring(idx + 1));
        }
        return new ParsedModuleStringNotation(notation, null);
    }

    private static class RichVersion {
        public static final RichVersion EMPTY = new RichVersion(null, null, null);

        final String require;
        final String strictly;
        final String prefer;

        private RichVersion(String require, String strictly, String prefer) {
            this.require = require;
            this.strictly = strictly;
            this.prefer = prefer;
        }
    }
}
