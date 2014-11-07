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

import org.gradle.play.internal.routes.spec.RoutesCompileSpec;
import org.gradle.play.internal.routes.spec.versions.RoutesCompileSpecV22X;
import org.gradle.play.internal.routes.spec.versions.RoutesCompileSpecV23X;

import java.io.File;
import java.util.List;
import java.util.Set;

public class RoutesCompileSpecFactory {

    public static RoutesCompileSpec create(Set<File> files, File outputDirectory, List<String> additionalImports, boolean namespaceReverseRouter,  boolean javaProject, RoutesCompilerVersion version) {
        switch (version){
            case V_22X:
                return new RoutesCompileSpecV22X(files, outputDirectory, additionalImports, javaProject);
            case V_23X:
                return new RoutesCompileSpecV23X(files, outputDirectory, additionalImports, namespaceReverseRouter, javaProject);
            default:
                throw new RuntimeException("Could not create routes compile spec for version: " + version);
        }

    }
}
