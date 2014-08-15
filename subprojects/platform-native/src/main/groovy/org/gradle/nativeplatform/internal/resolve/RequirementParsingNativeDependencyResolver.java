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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.nativeplatform.NativeLibraryRequirement;

public class RequirementParsingNativeDependencyResolver implements NativeDependencyResolver {
    private final NotationParser<Object, NativeLibraryRequirement> parser = NativeDependencyNotationParser.parser();

    private final NativeDependencyResolver delegate;

    public RequirementParsingNativeDependencyResolver(NativeDependencyResolver delegate) {
        this.delegate = delegate;
    }

    public void resolve(NativeBinaryResolveResult nativeBinaryResolveResult) {
        for (NativeBinaryRequirementResolveResult resolution : nativeBinaryResolveResult.getPendingResolutions()) {
            NativeLibraryRequirement requirement = parser.parseNotation(resolution.getInput());
            resolution.setRequirement(requirement);
        }
        delegate.resolve(nativeBinaryResolveResult);
    }
}
