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
package org.gradle.integtests

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule

@RunWith(DistributionIntegrationTestRunner.class)
class MavenProjectIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    private final GradleExecuter executer = dist.executer;

    @Test
    public void handlesSubProjectsWithoutTheMavenPluginApplied() {
        dist.testFile("settings.gradle").write("include 'subProject'");
        dist.testFile("build.gradle") << '''
            apply plugin: 'java'
            apply plugin: 'maven'
        '''
        executer.withTaskList().run();
    }

    @Test
    public void canDeployAProjectWithDependencyInMappedAndUnMappedConfiguration() {
        dist.testFile("build.gradle") << '''
            apply plugin: 'java'
            apply plugin: 'maven'
            group = 'root'
            repositories { mavenCentral() }
            configurations { custom }
            dependencies {
                custom 'commons-collections:commons-collections:3.2'
                runtime 'commons-collections:commons-collections:3.2'
            }
            uploadArchives {
                repositories {
                    mavenDeployer {
                        repository(url: "file://localhost/$projectDir/pomRepo/")
                    }
                }
            }
        '''
        executer.withTasks('uploadArchives').run()
    }
    
    @Test
    public void canDeployAProjectWithNoMainArtifact() {
        def file = dist.testFile("build.gradle") << '''
            apply plugin: 'java'
            apply plugin: 'maven'
            group = 'root'
            jar.enabled = false
            task sourceJar(type: Jar) {
                classifier = 'source'
            }
            artifacts {
                archives sourceJar
            }
            uploadArchives {
                repositories {
                    mavenDeployer {
                        repository(url: "file://localhost/$projectDir/pomRepo/")
                    }
                }
            }
        '''
        println file.absolutePath
        executer.withTasks('uploadArchives').run()
    }
}
