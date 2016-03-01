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
package org.gradle.language.assembler;

import org.gradle.api.Incubating;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.Managed;

/**
 * A set of assembly language sources.
 *
 * <pre autoTested="true">
 * apply plugin: "assembler"
 *
 * model {
 *     components {
 *         main(NativeLibrarySpec) {
 *             sources {
 *                 asm {
 *                     source {
 *                         srcDirs "src/main/i386", "src/shared/asm"
 *                         include "**{@literal /}*.s"
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 * </pre>
 */
@Incubating @Managed
public interface AssemblerSourceSet extends LanguageSourceSet {
}
