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
package org.gradle.plugins.ide.eclipse.model

class ProjectDependency extends AbstractClasspathEntry {

    String gradlePath
    String declaredConfigurationName

    ProjectDependency(Node node) {
        super(node)
        assertPathIsValid()
    }

    ProjectDependency(String path, String gradlePath) {
        super(path)
        assertPathIsValid()
        this.gradlePath = gradlePath
    }

    private void assertPathIsValid() {
        assert path.startsWith('/')
    }

    String getKind() {
        'src'
    }

    public String toString() {
        return "ProjectDependency{" + super.toString()
    }
}