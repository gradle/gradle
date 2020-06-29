/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.generator

class MavenPom {
    final Map<String, MavenScope> scopes = [:]

    MavenPom(File pomFile) {
        def pom = new XmlParser().parse(pomFile)
        pom.dependencies.dependency.each { dep ->
            def scopeElement = dep.scope
            def scopeName = scopeElement ? scopeElement.text() : "runtime"
            def scope = scopes[scopeName]
            if (!scope) {
                scope = new MavenScope()
                scopes[scopeName] = scope
            }
            scope.addDependency(dep.groupId.text(), dep.artifactId.text(), dep.version.text())
        }
    }
}
