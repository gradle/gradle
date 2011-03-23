/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

import org.gradle.util.Matchers
import spock.lang.Specification

class ModulePathTest extends Specification {
    def pathsAreEqualWhenTheirPathAndFilePathAreEqual() {
        Path path = new Path('url')
        String filePath = 'path'

        expect:
        Matchers.strictlyEquals(new ModulePath(path, filePath), new ModulePath(path, filePath))
        new ModulePath(path, filePath) != new ModulePath(path, 'other')
        new ModulePath(path, filePath) != new ModulePath(new Path('other'), filePath)
    }
}
