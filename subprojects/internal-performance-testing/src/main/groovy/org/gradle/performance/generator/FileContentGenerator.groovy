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

package org.gradle.performance.generator

class FileContentGenerator {

    TestProjectGeneratorConfiguration config

    FileContentGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config
    }

    def generateBuildGradle(Integer subProjectNumber, DependencyTree dependencyTree) {
        def isRoot = subProjectNumber == null
        if (isRoot && config.subProjects > 0) {
            return ""
        }
        """
        def missingJavaLibrarySupport = GradleVersion.current() < GradleVersion.version('3.4')

        ${config.plugins.collect { decideOnJavaPlugin(it, dependencyTree.hasParentNodeSets(subProjectNumber)) }.join("\n        ") }
        
        String compilerMemory = getProperty('compilerMemory')
        String testRunnerMemory = getProperty('testRunnerMemory')
        int testForkEvery = getProperty('testForkEvery') as Integer

        repositories {
            ${config.repositories.join("\n            ")}
        }
        ${dependenciesBlock('api', 'implementation', 'testImplementation', subProjectNumber, dependencyTree)}             
        tasks.withType(JavaCompile) {
            options.fork = true
            options.incremental = true
            options.forkOptions.memoryInitialSize = compilerMemory
            options.forkOptions.memoryMaximumSize = compilerMemory
        }
        
        tasks.withType(Test) {
            ${config.useTestNG ? 'useTestNG()' : ''}
            minHeapSize = testRunnerMemory
            maxHeapSize = testRunnerMemory
            maxParallelForks = 8
            forkEvery = testForkEvery
            
            if (!JavaVersion.current().java8Compatible) {
                jvmArgs '-XX:MaxPermSize=512m'
            }
            jvmArgs '-XX:+HeapDumpOnOutOfMemoryError'
        }

        task dependencyReport(type: DependencyReportTask) {
            outputs.upToDateWhen { false }
            outputFile = new File(buildDir, "dependencies.txt")
        }
        """
    }

    def generateSettingsGradle(boolean isRoot) {
        if (!isRoot) {
            return null
        }
        if (config.subProjects == 0) {
            return ""
        }
        """ 
        ${(0..config.subProjects-1).collect { "include 'project$it'" }.join("\n")}
        """
    }

    def generateGradleProperties(boolean isRoot) {
        if (!isRoot) {
            return null
        }
        """
        org.gradle.jvmargs=-Xmxs${config.daemonMemory} -Xmx${config.daemonMemory}
        org.gradle.parallel=${config.parallel}
        compilerMemory=${config.compilerMemory}
        testRunnerMemory=${config.testRunnerMemory}
        testForkEvery=${config.testForkEvery}
        """
    }

    def generatePomXML(boolean isRoot) {
        def body
        def hasSources = !isRoot || config.subProjects == 0
        if (!hasSources) {
            body = """
            <modules>
                ${(0..config.subProjects-1).collect { "<module>project$it</module>" }.join("\n                ")}
            </modules>
            """
        } else {
            body  = """
            <dependencies>
                ${config.externalApiDependencies.collect { convertToPomDependency(it) }.join()}
                ${config.externalImplementationDependencies.collect { convertToPomDependency(it) }.join()}
            </dependencies>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-report-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>test-report</id>
                                <goals>
                                    <goal>report-only</goal>
                                </goals>
                                <phase>verify</phase>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        }
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>
            <groupId>org.gradle</groupId>
            <artifactId>root</artifactId>
            <packaging>${hasSources ? 'jar' : 'pom'}</packaging>
            <version>1.0</version>
            $body
        </project>
        """
    }

    def generateProductionClassFile(Integer subProjectNumber, int classNumber, DependencyTree dependencyTree) {
        def properties = ''
        def ownPackageName = packageName(classNumber, subProjectNumber)
        def imports = ''
        (0..propertyCount-1).each {
            def children = dependencyTree.parentToChildrenNodes.get(classNumber)
            def propertyType
            if (it < children.size()) {
                def childNumber = children.get(it)
                propertyType = "Production${childNumber}"
                def childPackageName = packageName(childNumber, config.subProjects == 0 ? null : dependencyTree.findNodeSet(childNumber))
                if (childPackageName != ownPackageName) {
                    imports += imports == '' ? '\n        ' : ''
                    imports += "import ${childPackageName}.Production${childNumber};\n        "
                }
            } else {
                propertyType = "String"
            }
            properties += """
            private $propertyType property$it;

            public $propertyType getProperty$it() {
                return property$it;
            }
        
            public void setProperty$it($propertyType value) {
                property$it = value;
            }
            """
        }

        """
        package ${ownPackageName};
        ${imports}
        public class Production$classNumber {        
            $properties    
        }   
        """
    }

    def generateTestClassFile(Integer subProjectNumber, int classNumber, DependencyTree dependencyTree) {
        def testMethods = ""
        def ownPackageName = packageName(classNumber, subProjectNumber)
        def imports = ''
        (0..propertyCount-1).each {
            def children = dependencyTree.parentToChildrenNodes.get(classNumber)
            def propertyType
            def propertyValue
            if (it < children.size()) {
                def childNumber = children.get(it)
                propertyType = "Production${childNumber}"
                propertyValue = "new Production${childNumber}()"
                def childPackageName = packageName(childNumber, dependencyTree.findNodeSet(childNumber))
                if (childPackageName != ownPackageName) {
                    imports += imports == '' ? '\n        ' : ''
                    imports += "import ${childPackageName}.Production${childNumber};\n        "
                }
            } else {
                propertyType = "String"
                propertyValue = '"value"'
            }
            testMethods += """
            @Test
            public void testProperty$it() {
                $propertyType value = $propertyValue;
                objectUnderTest.setProperty$it(value);
                assertEquals(value, objectUnderTest.getProperty$it());
            }
            """
        }

        """
        package ${packageName(classNumber, subProjectNumber)};
        ${imports}
        import org.${config.useTestNG ? 'testng.annotations' : 'junit'}.Test;
        import static org.${config.useTestNG ? 'testng' : 'junit'}.Assert.*;
        
        public class Test$classNumber {  
            Production$classNumber objectUnderTest = new Production$classNumber();     
            $testMethods
        }
        """
    }

    def packageName(int classNumber, Integer subProjectNumber = null, String separator = '.') {
        def projectPackage = subProjectNumber == null ? "" : "${separator}project$subProjectNumber"
        def subPackage = "${separator}p${(int) (classNumber / 20)}"
        "org${separator}gradle${separator}test${separator}performance${separator}${config.projectName.toLowerCase()}${projectPackage}$subPackage"
    }

    private getPropertyCount() {
        Math.ceil(config.linesOfCodePerSourceFile / 10)
    }

    private decideOnJavaPlugin(String plugin, boolean projectHasParents) {
        if (plugin.contains('java')) {
            if (projectHasParents) {
                return "apply plugin: missingJavaLibrarySupport ? 'java' : 'java-library'"
            } else {
                return "apply plugin: 'java'"
            }
        }
        "apply plugin: '$plugin'"
    }

    private dependenciesBlock(String api, String implementation, String testImplementation, Integer subProjectNumber, DependencyTree dependencyTree) {
        def hasParent = dependencyTree.hasParentNodeSets(subProjectNumber)
        def subProjectNumbers = dependencyTree.parentToChildrenNodeSets.get(subProjectNumber)
        def subProjectDependencies = ''
        if (subProjectNumbers?.size() > 0) {
            def abiProjectNumber = subProjectNumbers.get(0)
            subProjectDependencies = subProjectNumbers.collect { it == abiProjectNumber ? "${hasParent ? api : implementation} project(':project${abiProjectNumber}')" : "$implementation project(':project$it')" }.join("\n            ")
        }
        """
        if (missingJavaLibrarySupport) {
            configurations {
                ${hasParent ? 'api' : ''}
                implementation
                testImplementation
                ${hasParent ? 'compile.extendsFrom api' : ''}
                compile.extendsFrom implementation
                testCompile.extendsFrom testImplementation
            }
        }

        dependencies {
            ${config.externalApiDependencies.collect { "${hasParent ? api : implementation} '$it'" }.join("\n            ")}
            ${config.externalImplementationDependencies.collect { "$implementation '$it'" }.join("\n            ")}
            $testImplementation '${config.useTestNG ? 'org.testng:testng:6.4' : 'junit:junit:4.12'}'
                        
            $subProjectDependencies
        }
        """
    }

    private convertToPomDependency(String dependency) {
        def parts = dependency.split(':')
        def groupId = parts[0]
        def artifactId = parts[1]
        def version = parts[2]
                """
                <dependency>
                    <groupId>$groupId</groupId>
                    <artifactId>$artifactId</artifactId>
                    <version>$version</version>
                </dependency>"""
    }
}
