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

import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.language.Language

import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

abstract class FileContentGenerator {

    static FileContentGenerator forConfig(TestProjectGeneratorConfiguration config) {
        switch (config.dsl) {
            case KOTLIN:
                return new KotlinDslFileContentGenerator(config)
            case GradleDsl.GROOVY:
                return new GroovyDslFileContentGenerator(config)
        }
    }

    protected final TestProjectGeneratorConfiguration config

    FileContentGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config
    }

    def generateBuildGradle(Language language, Integer subProjectNumber, DependencyTree dependencyTree) {
        def isRoot = subProjectNumber == null
        if (isRoot && config.subProjects > 0) {
            if (config.compositeBuild) {
                return """
                ${createTaskThatDependsOnAllIncludedBuildsTaskWithSameName('clean')}
                ${createTaskThatDependsOnAllIncludedBuildsTaskWithSameName('assemble')}
                """
            }
            return ""
        }
        return """
        import org.gradle.util.GradleVersion

        ${missingJavaLibrarySupportFlag()}
        ${noJavaLibraryPluginFlag()}

        ${config.plugins.collect { decideOnJavaPlugin(it, dependencyTree.hasParentProject(subProjectNumber)) }.join("\n        ")}
        
        repositories {
            ${config.repositories.join("\n            ")}
        }
        ${dependenciesBlock('api', 'implementation', 'testImplementation', subProjectNumber, dependencyTree)}             

        allprojects {
            dependencies{
        ${
            language == Language.GROOVY ? directDependencyDeclaration('implementation', 'org.codehaus.groovy:groovy:2.5.8') : ""
        }
            }
        }


        ${tasksConfiguration()}

        group = "org.gradle.test.performance"
        version = "2.0"
        """
    }

    def generateSettingsGradle(boolean isRoot) {
        if (config.compositeBuild) {
            if (!isRoot) {
                return ""
            }
            return (0..config.subProjects - 1).collect {
                if (config.compositeBuild.usePredefinedPublications()) {
                    """
                    includeBuild("project$it") {
                        dependencySubstitution {
                            substitute(module("org.gradle.test.performance:project${it}")).with(project(":"))
                        }
                    }
                    """
                } else {
                    "includeBuild(\"project$it\")"
                }
            }.join("\n")
        } else {
            if (!isRoot) {
                return null
            }

            String includedProjects = ""
            if (config.subProjects != 0) {
                includedProjects = """ 
                    ${(0..config.subProjects - 1).collect { "include(\"project$it\")" }.join("\n")}
                """
            }

            return includedProjects + """
                def enableFeaturePreviewSafe(String feature) {
                     try {
                        enableFeaturePreview(feature)
                        println "Enabled feature preview " + feature
                     } catch(Exception ignored) {
                        println "Failed to enable feature preview " + feature
                     }
                }

                ${config.featurePreviews.collect { "enableFeaturePreviewSafe(\"$it\")" }.join("\n")}
            """
        }
    }

    def generateGradleProperties(boolean isRoot) {
        if (!isRoot && !config.compositeBuild) {
            return null
        }
        """
        org.gradle.jvmargs=-Xmxs${config.daemonMemory} -Xmx${config.daemonMemory}
        org.gradle.parallel=${config.parallel}
        org.gradle.workers.max=${config.maxWorkers}
        compilerMemory=${config.compilerMemory}
        testRunnerMemory=${config.testRunnerMemory}
        testForkEvery=${config.testForkEvery}
        ${->
            config.systemProperties.entrySet().collect { "systemProp.${it.key}=${it.value}" }.join("\n")
        }
        """
    }

    def generatePomXML(Integer subProjectNumber, DependencyTree dependencyTree) {
        def body = ""
        def isParent = subProjectNumber == null || config.subProjects == 0
        def hasSources = subProjectNumber != null || config.subProjects == 0
        if (isParent) {
            if (config.subProjects != 0) {
                body += """
                    <modules>
                        ${(0..config.subProjects - 1).collect { "<module>project$it</module>" }.join("\n                ")}
                    </modules>
                """
            }
            body += """
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.8.0</version>
                        <configuration>
                            <source>1.8</source>
                            <target>1.8</target>
                            <fork>true</fork>
                            <meminitial>${config.compilerMemory}</meminitial>
                            <maxmem>${config.compilerMemory}</maxmem>
                        </configuration>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <version>2.19.1</version>
                        <configuration>
                            <forkCount>${config.maxParallelForks}</forkCount>
                            <reuseForks>true</reuseForks>
                            <argLine>-Xms${config.testRunnerMemory} -Xmx${config.testRunnerMemory}</argLine>
                        </configuration>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-report-plugin</artifactId>
                        <version>2.19.1</version>
                        <executions>
                            <execution>
                                <id>test-report</id>
                                <goals>
                                    <goal>report-only</goal>
                                </goals>
                                <phase>test</phase>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
            """
        } else {
            body += """
                <parent>
                    <groupId>org.gradle.test.performance</groupId>
                    <artifactId>project</artifactId>
                    <version>1.0</version>
                </parent>
            """
        }
        if (hasSources) {
            def subProjectNumbers = dependencyTree.getChildProjectIds(subProjectNumber)
            def subProjectDependencies = ''
            if (subProjectNumbers?.size() > 0) {
                subProjectDependencies = subProjectNumbers.collect { convertToPomDependency("org.gradle.test.performance:project$it:1.0") }.join()
            }
            body += """
            <dependencies>
                ${config.externalApiDependencies.collect { convertToPomDependency(it) }.join()}
                ${config.externalImplementationDependencies.collect { convertToPomDependency(it) }.join()}
                ${convertToPomDependency('junit:junit:4.12', 'test')}
                ${subProjectDependencies}
            </dependencies>
            """
        }
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>
            <groupId>org.gradle.test.performance</groupId>
            <artifactId>project${subProjectNumber == null ? '' : subProjectNumber}</artifactId>
            <packaging>${hasSources ? 'jar' : 'pom'}</packaging>
            <version>1.0</version>
            $body
        </project>
        """
    }

    def generatePerformanceScenarios(boolean isRoot) {
        if (isRoot) {
            def fileToChange = config.fileToChangeByScenario['assemble']
            """
                nonAbiChange {
                  tasks = ["assemble"]
                  apply-non-abi-change-to = "${fileToChange}"
                  maven {
                    targets = ["clean", "package", "-Dmaven.test.skip=true", "-T", "4"]
                  }
                }
                
                abiChange {
                  tasks = ["assemble"]
                  apply-abi-change-to = "${fileToChange}"
                  maven {
                    targets = ["clean", "package", "-Dmaven.test.skip=true", "-T", "4"]
                  }
                }
                
                cleanAssemble {
                  tasks = ["clean", "assemble"]
                   maven {
                    targets = ["clean", "package", "-Dmaven.test.skip=true", "-T", "4"]
                  }
                }
                
                cleanAssembleCached {
                  tasks = ["clean", "assemble"]
                  gradle-args = ["--build-cache"]
                }
                
                cleanBuild {
                  tasks = ["clean", "build"]
                  maven {
                    targets = ["clean", "test", "package", "-T", "4"]
                  }
                }
                
                cleanBuildCached {
                  tasks = ["clean", "build"]
                  maven {
                    targets = ["clean", "test", "package", "-T", "4"]
                  }
                  gradle-args = ["--build-cache"]
                }
                
                incrementalCompile {
                  tasks = ["compileJava"]
                   maven {
                    targets = ["compile", "-T", "4"]
                  }
                  apply-non-abi-change-to = "${fileToChange}"
                }
                
                incrementalTest {
                  tasks = ["build"]
                  apply-non-abi-change-to = "${fileToChange}"
                   maven {
                    targets = ["test", "-T", "4"]
                  }
                }
                
                cleanTest {
                  tasks = ["clean", "test"]
                  maven {
                    targets = ["clean", "test", "-T", "4"]
                  }
                }
                
                cleanTestCached {
                  tasks = ["clean", "test"]
                  gradle-args = ["--build-cache"]
                }

            """.stripIndent()
        }
    }

    def generateProductionClassFile(Integer subProjectNumber, int classNumber, DependencyTree dependencyTree) {
        def properties = ''
        def ownPackageName = packageName(classNumber, subProjectNumber)
        def imports = ''
        def children = dependencyTree.getTransitiveChildClassIds(classNumber)
        (0..Math.max(propertyCount, children.size()) - 1).each {
            def propertyType
            if (it < children.size()) {
                def childNumber = children.get(it)
                propertyType = "Production${childNumber}"
                def childPackageName = packageName(childNumber, config.subProjects == 0 ? null : dependencyTree.getProjectIdForClass(childNumber))
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
        def children = dependencyTree.getTransitiveChildClassIds(classNumber)
        (0..Math.max(propertyCount, children.size()) - 1).each {
            def propertyType
            def propertyValue
            if (it < children.size()) {
                def childNumber = children.get(it)
                propertyType = "Production${childNumber}"
                propertyValue = "new Production${childNumber}()"
                def childPackageName = packageName(childNumber, config.subProjects == 0 ? null : dependencyTree.getProjectIdForClass(childNumber))
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

    protected final getPropertyCount() {
        Math.ceil(config.minLinesOfCodePerSourceFile / 10)
    }

    protected final String decideOnJavaPlugin(String plugin, Boolean projectHasParents) {
        if (plugin.contains('java')) {
            if (projectHasParents) {
                return """
                    if (missingJavaLibrarySupport || noJavaLibraryPlugin) {
                        ${imperativelyApplyPlugin("java")}
                    } else {
                        ${imperativelyApplyPlugin("java-library")}
                    }
                """
            } else {
                return imperativelyApplyPlugin("java")
            }
        }
        return imperativelyApplyPlugin(plugin)
    }

    private dependenciesBlock(String api, String implementation, String testImplementation, Integer subProjectNumber, DependencyTree dependencyTree) {
        def hasParent = dependencyTree.hasParentProject(subProjectNumber)
        def subProjectNumbers = dependencyTree.getChildProjectIds(subProjectNumber)
        def subProjectDependencies = ''
        if (subProjectNumbers?.size() > 0) {
            def abiProjectNumber = subProjectNumbers.get(DependencyTree.API_DEPENDENCY_INDEX)
            subProjectDependencies = subProjectNumbers.collect {
                it == abiProjectNumber ? projectDependencyDeclaration(hasParent ? api : implementation, abiProjectNumber) : projectDependencyDeclaration(implementation, it)
            }.join("\n            ")
        }
        def block = """
                    ${config.externalApiDependencies.collect { directDependencyDeclaration(hasParent ? api : implementation, it) }.join("\n            ")}
                    ${config.externalImplementationDependencies.collect { directDependencyDeclaration(implementation, it) }.join("\n            ")}
                    ${directDependencyDeclaration(testImplementation, config.useTestNG ? 'org.testng:testng:6.4' : 'junit:junit:4.12')}
    
                    $subProjectDependencies
        """
        return """
            ${configurationsIfMissingJavaLibrarySupport(hasParent)}
            if (hasProperty("compileConfiguration")) {
                dependencies {
                    ${block.replace(api, 'compile').replace(implementation, 'compile').replace(testImplementation, 'testCompile')}
                }
            } else {
                dependencies {
                    $block
                }
            }
        """
    }

    protected final convertToPomDependency(String dependency, String scope = 'compile') {
        def parts = dependency.split(':')
        def groupId = parts[0]
        def artifactId = parts[1]
        def version = parts[2]
        """
                <dependency>
                    <groupId>$groupId</groupId>
                    <artifactId>$artifactId</artifactId>
                    <version>$version</version>
                    <scope>$scope</scope>
                </dependency>"""
    }

    protected abstract String missingJavaLibrarySupportFlag()

    protected abstract String noJavaLibraryPluginFlag()

    protected abstract String tasksConfiguration()

    protected abstract String imperativelyApplyPlugin(String plugin)

    protected abstract String createTaskThatDependsOnAllIncludedBuildsTaskWithSameName(String taskName)

    protected abstract String configurationsIfMissingJavaLibrarySupport(boolean hasParent)

    protected abstract String directDependencyDeclaration(String configuration, String notation)

    protected abstract String projectDependencyDeclaration(String configuration, int projectNumber)

    protected final String dependency(int projectNumber) {
        if (config.compositeBuild) {
            return "\"org.gradle.test.performance:project${projectNumber}:1.0\""
        }
        return "project(\":project${projectNumber}\")"
    }
}
