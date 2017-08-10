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
        """repositories {
               ${jcenterRepositoryDefinition()}
           }
        """
    }

    static String mavenCentralRepository() {
        """repositories {
               ${mavenCentralRepositoryDefinition()}
           }
        """
    }

    static String jcenterRepositoryDefinition() {
        mavenRepositoryDefinition('org.gradle.integtest.mirrors.jcenter', 'jcenter-remote', 'jcenter()')
    }

    static mavenCentralRepositoryDefinition() {
        mavenRepositoryDefinition('org.gradle.integtest.mirrors.mavencentral', 'repo1-remote', 'mavenCentral()')
    }

    static typesafeMavenRepositoryDefinition() {
        String defaultRepo = '''
           maven {
               name "typesafe-maven-release"
               url "https://repo.typesafe.com/typesafe/maven-releases"
           }'''
        mavenRepositoryDefinition('org.gradle.integtest.mirrors.typesafemaven', 'typesafe-maven-release-remote', defaultRepo)
    }

    static typesafeIvyRepositoryDefinition() {
        String repoUrl = System.getProperty('org.gradle.integtest.mirrors.typesafeivy')
        repoUrl = repoUrl ?: "https://repo.typesafe.com/typesafe/ivy-releases"
        """
            ivy {
                name "typesafe-ivy-release"
                url ${repoUrl}
                layout "ivy"
            }
        """
    }

    private static mavenRepositoryDefinition(String repoUrlProperty, String repoName, String defaultRepo) {
        String repoUrl = System.getProperty(repoUrlProperty)
        if (repoUrl) {
            """maven {
                   name '${repoName}'
                   url '${repoUrl}'
               }
            """
        } else {
            defaultRepo
        }
    }
}
