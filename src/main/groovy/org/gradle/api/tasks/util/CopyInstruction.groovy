/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.util

import org.gradle.api.InvalidUserDataException

/**
 * @author Hans Dockter
 *
 * todo: Refactor to use FileSet
 * todo: Refactor to accept an existing ant instance
 */
class CopyInstruction {
    AntBuilder antBuilder
    File sourceDir
    File targetDir
    Set includes = []
    Set excludes = []
    Map filters = [:]

    void execute() {
        if (!sourceDir) {throw new InvalidUserDataException('Source dir must not be null!')}
        if (!targetDir) {throw new InvalidUserDataException('Target dir must not be null!')}
        antBuilder.copy(todir: targetDir) {
            fileset(dir: sourceDir) {
                includes.each {String pattern ->
                    include(name: pattern)
                }
                excludes.each {String pattern ->
                    exclude(name: pattern)
                }
            }
            filterset() {
                this.filters.each {String token, String value ->
                    filter(token: token, value: value)
                }
            }
        }
    }
}
