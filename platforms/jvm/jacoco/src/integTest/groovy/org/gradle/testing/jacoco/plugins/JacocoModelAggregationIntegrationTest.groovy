/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JacocoModelAggregationIntegrationTest extends AbstractIntegrationSpec {

    /**
     * Potential problems:
     * - How to deal with dimensions? (java versions x type of test)
     * - Internal and external consumption become different (example of publishing test results)
     * - Type-safety for composite builds is problematic
     */
    def "aggregate subprojects jacoco data in root project"() {
        given:
        addSources()

        settingsFile """
            include("a")
            include("b")
        """

        buildFile "buildSrc/build.gradle", """
            plugins { id 'groovy-gradle-plugin' }
        """

        groovyFile "buildSrc/src/main/groovy/JacocoData.groovy", """
            class JacocoData implements Serializable {
                Collection<File> executionData
                Collection<File> sourcesDirectories
                Collection<File> classDirectories
            }
        """

        buildFile "buildSrc/src/main/groovy/my-plugin.gradle", """
            plugins {
                id "java-library"
                id "jacoco"
            }

            Provider<File> execDataProvider = tasks.named("test").map { it ->
                it.extensions.getByType(JacocoTaskExtension).destinationFile
            }

            Provider<JacocoData> jacocoData = execDataProvider.map { File it ->
                new JacocoData(executionData: [it], sourcesDirectories: [], classDirectories: [])
            }
            isolated.models.register("jacocoData", JacocoData, jacocoData)
        """

        buildFile "buildSrc/src/main/groovy/my-plugin.gradle", """
            repositories { ${mavenCentralRepository()} }
            test { useJUnit() }
            dependencies { testImplementation "junit:junit:4.13" }
        """

        buildFile """
            abstract class JacocoAggregatedReport extends DefaultTask {
                @InputDirectory abstract DirectoryProperty getRootDir()
                @Input abstract MapProperty<String, JacocoData> getJacocoDataByProject()
                @OutputFile abstract RegularFileProperty getOutput()

                @TaskAction run() {
                    def outputFile = output.get().asFile
                    def data = jacocoDataByProject.get().toSorted()

                    def root = rootDir.get().asFile.toPath()
                    def projects = data.keySet().toSorted()
                    for (def projectName : projects) {
                        def jacocoData = data.get(projectName)
                        def execDataPaths = jacocoData.executionData.collect { root.relativize(it.toPath()).toString() }
                        outputFile << "\\n"
                        outputFile << projectName + ": executionData=" + execDataPaths
                    }
                }
            }

            Map<IsolatedProject, Provider<JacocoData>> modelMap = isolated.models.fromProjects("jacocoData", JacocoData, subprojects)

            tasks.register("jacocoAggregatedReport", JacocoAggregatedReport) {
                rootDir = project.rootDir
                modelMap.entrySet().each { entry ->
                    jacocoDataByProject.put(entry.key.name, entry.value)
                }
                output = layout.buildDirectory.file("out.txt")
            }
        """

        buildFile "a/build.gradle", """
            plugins { id "my-plugin" }
        """
        buildFile "b/build.gradle", """
            plugins { id "my-plugin" }
        """

        when:
        succeeds(":jacocoAggregatedReport")

        then: "model dependencies trigger tasks that produce the data"
        executed(":a:test", ":b:test")

        and:
        file("build/out.txt").text.trim() == """
            |a: executionData=[a/build/jacoco/test.exec]
            |b: executionData=[b/build/jacoco/test.exec]
        """.stripMargin('|').trim()
    }

    private def addSources() {
        javaFile "a/src/main/java/a/Adder.java", """
            package a;

            public class Adder {
                int add(int x, int y) {
                    return x+y;
                }
            }
        """
        javaFile "a/src/test/java/a/AdderTest.java", """
            package a;

            import org.junit.Assert;
            import org.junit.Test;

            public class AdderTest {
                @Test
                public void testAdd() {
                    Adder adder = new Adder();
                    Assert.assertEquals(2, adder.add(1, 1));
                    Assert.assertEquals(4, adder.add(2, 2));
                    Assert.assertEquals(3, adder.add(1, 2));
                }
            }
        """

        javaFile "b/src/main/java/b/Multiplier.java", """
            package b;

            public class Multiplier {
                int multiply(int x, int y) {
                    return x*y;
                }
            }
        """
        javaFile "b/src/test/java/b/MultiplierTest.java", """
            package b;

            import org.junit.Assert;
            import org.junit.Test;

            public class MultiplierTest {
                @Test
                public void testMultiply() {
                    Multiplier multiplier = new Multiplier();
                    Assert.assertEquals(1, multiplier.multiply(1, 1));
                    Assert.assertEquals(4, multiplier.multiply(2, 2));
                    Assert.assertEquals(2, multiplier.multiply(1, 2));
                }
            }
        """
    }


}
