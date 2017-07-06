/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.scala.compile

import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest

import java.nio.file.Files

class ScalaCompileRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {

    @Override
    protected String getTaskName() {
        return ":compileScala"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        def classes = new ScalaIncrementalCompilationFixture(testDirectory)
        classes.baseline()

        buildFile << buildFileWithScalaSrcDir('scala')
    }

    private static String buildFileWithScalaSrcDir(String srcDir) {
        def zincVersion = '0.3.13'
        def scalaVersion = '2.11.11'
        """
            apply plugin: 'scala'
            
            repositories {
                jcenter()
            }

            dependencies {
                zinc "com.typesafe.zinc:zinc:${zincVersion}"
                compile "org.scala-lang:scala-library:${scalaVersion}" 
            }
            
            sourceSets {
                main {
                    scala {
                        srcDirs = ['src/main/${srcDir}']
                    }
                }
            }
            
            sourceCompatibility = '1.7'
            targetCompatibility = '1.7'
        """
    }

    @Override
    protected void moveFilesAround() {
        Files.move(file("src/main/scala").toPath(), file("src/main/new-scala").toPath())
        buildFile.text = buildFileWithScalaSrcDir("new-scala")
        executer.requireOwnGradleUserHomeDir()
    }

    @Override
    protected extractResults() {
        return new ScalaIncrementalCompilationFixture(testDirectory).classDependingOnBasicClassSource.compiledClass.bytes
    }
}
