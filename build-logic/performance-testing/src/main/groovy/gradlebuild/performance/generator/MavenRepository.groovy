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

class MavenRepository {
    int depth = 1
    final File rootDir
    List<MavenModule> modules = []
    MavenJarCreator mavenJarCreator = new MavenJarCreator()

    MavenRepository(File rootDir) {
        println rootDir
        this.rootDir = rootDir
    }

    URI getUri() {
        return rootDir.toURI()
    }

    MavenModule addModule(String groupId, String artifactId, Object version = '1.0') {
        def artifactDir = new File(rootDir, "${groupId.replace('.', '/')}/$artifactId/$version")
        def module = new MavenModule(artifactDir, groupId, artifactId, version as String)
        module.mavenJarCreator = mavenJarCreator
        modules << module
        return module
    }

    void publish() {
        modules.each {
            it.publish()
        }
    }

    List<MavenModule> getDependenciesOfTransitiveLevel(int level) {
        return modules.findAll { ((int) (it.artifactId - "artifact").toInteger() % depth) == level - 1 }
    }
}
