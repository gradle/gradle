/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.model.cpp;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.DomainObjectSet;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Represents the compilation details for a binary.
 *
 * @since 4.10
 */
@Incubating
public interface CompilationDetails {
    /**
     * All framework search paths.
     */
    List<File> getFrameworkSearchPaths();

    /**
     * All compilation unit used for this binary.
     */
    List<File> getSources();

    /**
     * All macro define directives.
     */
    DomainObjectSet<? extends MacroDirective> getMacroDefines();

    /**
     * All macro undefine directives.
     */
    Set<String> getMacroUndefines();

    /**
     * All system search paths.
     */
    List<File> getSystemHeaderSearchPaths();

    /**
     * All user search paths.
     */
    List<File> getUserHeaderSearchPaths();

    /**
     * Returns additional tool arguments.
     */
    List<String> getAdditionalArgs();
}
