/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyMetadataImpl;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

public class DependencyMetadataNotationParser {
    public static NotationParser<Object, DependencyMetadata> parser(Instantiator instantiator) {
        return NotationParserBuilder
            .toType(DependencyMetadata.class)
            .fromCharSequence(new DependencyStringNotationConverter<DependencyMetadataImpl>(instantiator, DependencyMetadataImpl.class))
            .converter(new DependencyMapNotationConverter<DependencyMetadataImpl>(instantiator, DependencyMetadataImpl.class))
            .invalidNotationMessage("Comprehensive documentation on dependency notations is available in DSL reference for DependencyHandler type.")
            .toComposite();
    }
}
