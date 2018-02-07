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

    static String googleRepository() {
        return """
            repositories {
                ${googleRepositoryDefinition()}
            }
        """
    }

    static String jcenterRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.jcenter')
        if (repoUrl) {
            return """
                maven {
                    name 'jcenter-remote'
                    url '${repoUrl}'
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
                    name 'repo1-remote'
                    url '${repoUrl}'
                }
            """
        } else {
            return 'mavenCentral()'
        }
    }

    static String lightbendMavenRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.lightbendmaven')
        if (repoUrl) {
            return """
                maven {
                    name 'lightbend-maven-release-remote'
                    url '${repoUrl}'
                }
            """
        } else {
            return """
                maven {
                    name 'lightbend-maven-release'
                    url 'https://repo.lightbend.com/lightbend/maven-releases'
                }
            """
        }
    }

    static String lightbendIvyRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.lightbendivy')
        if (repoUrl) {
            return """
                ivy {
                    name 'lightbend-ivy-release-remote'
                    url '${repoUrl}'
                    layout 'ivy'
                }
            """
        } else {
            return """
                ivy {
                    name 'lightbend-ivy-release'
                    url 'https://repo.lightbend.com/lightbend/ivy-releases'
                    layout 'ivy'
                }
            """
        }
    }

    static String googleRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.google')
        if (repoUrl) {
            return """
                maven {
                    name 'Google'
                    url '${repoUrl}'
                }
            """
        } else {
            return 'google()'
        }
    }
}
