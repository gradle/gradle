/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures

class RepoScriptBlockUtil {

    private RepoScriptBlockUtil() {
    }

    static String jcenterRepository() {
        return """
            repositories {
                ${jcenterRepositoryDefinition()}
            }
        """
    }

    static String mavenCentralRepository() {
        return """
            repositories {
                ${mavenCentralRepositoryDefinition()}
            }
        """
    }

    static String jcenterRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.jcenter')
        if (repoUrl) {
            return """
                maven {
                    name "jcenter-remote"
                    url "${repoUrl}"
                }
            """
        } else {
            return 'jcenter()'
        }
    }

    static String mavenCentralRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.mavencentral')
        if (repoUrl) {
            return """
                maven {
                    name "repo1-remote"
                    url "${repoUrl}"
                }
            """
        } else {
            return 'mavenCentral()'
        }
    }

    static String typesafeMavenRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.typesafemaven')
        if (repoUrl) {
            return """
                maven {
                    name "typesafe-maven-release-remote'"
                    url "${repoUrl}"
                }
            """
        } else {
            return """
                maven {
                    name "typesafe-maven-release"
                    url "https://repo.typesafe.com/typesafe/maven-releases"
                }
            """
        }
    }

    static String typesafeIvyRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.typesafeivy')
        if (repoUrl) {
            return """
                ivy {
                    name "typesafe-ivy-release-remote"
                    url "${repoUrl}"
                    layout "ivy"
                }
            """
        } else {
            return """
                ivy {
                    name "typesafe-ivy-release"
                    url "https://repo.typesafe.com/typesafe/ivy-releases"
                    layout "ivy"
                }
            """
        }
    }
}
