/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.routes;

import org.gradle.api.InvalidUserDataException;

public enum RoutesCompilerVersion {
    V_22X,
    V_23X;

    public static RoutesCompilerVersion parse(String version) {
        if (version == null) {
            throw new InvalidUserDataException("No version (version is null) of the Play Routes Compiler detected");
        }

        if (version.matches("2\\.2\\..*?")) {
            return V_22X;
        } else if (version.matches("2\\.3\\..*?")) {
            return V_23X;
        }
        throw new InvalidUserDataException("Could not find a compatible Play version for Routes Compiler. This plugin is compatible with: 2.3.x, 2.2.x");
    }
}
