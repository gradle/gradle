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

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.language.Language

import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

@CompileStatic
abstract class FileContentGenerator {
    static FileContentGenerator forConfig(TestProjectGeneratorConfiguration config) {
        switch (config.dsl) {
            case KOTLIN:
                return new KotlinDslFileContentGenerator(config)
            case GradleDsl.GROOVY:
                return new GroovyDslFileContentGenerator(config)
            default:
                throw new IllegalStateException("Should not be here!")
        }
    }

    protected final TestProjectGeneratorConfiguration config

    FileContentGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config
    }

    String generateVersionCatalog() {
        return """
        [libraries]
        groovy = "org.codehaus.groovy:groovy:2.5.22"
        testng = "org.testng:testng:6.4"
        junit = "junit:junit:4.13"

        ${config.externalApiDependencies
            .collect { "${it.key} = \"${it.value}\"" }
            .join("\n        ")}
        ${config.externalImplementationDependencies
            .collect { "${it.key} = \"${it.value}\"" }
            .join("\n        ")}
        """
    }

    String generateBuildGradle(Language language, Integer subProjectNumber, DependencyTree dependencyTree) {
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
        plugins {
            ${config.plugins.collect { decideOnJavaPlugin(it, dependencyTree.hasParentProject(subProjectNumber)) }.join("\n        ")}
        }

        group = "org.gradle.test.performance"
        version = "2.0"

        repositories {
            ${config.repositories.join("\n            ")}
        }

        ${dependenciesBlock('api', 'implementation', 'testImplementation', subProjectNumber, dependencyTree)}

        dependencies{
        ${
            language == Language.GROOVY ? versionCatalogDependencyDeclaration('implementation', 'groovy') : ""
        }
        }


        ${tasksConfiguration()}
        """
    }

    String generateSettingsGradle(boolean isRoot) {
        if (config.compositeBuild) {
            if (!isRoot) {
                return ""
            }
            return (0..config.subProjects - 1).collect {
                if (config.compositeBuild.usePredefinedPublications()) {
                    """
                    includeBuild("project$it") {
                        dependencySubstitution {
                            substitute(module("org.gradle.test.performance:project${it}")).using(project(":"))
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

            return includedProjects + generateEnableFeaturePreviewCode()
        }
    }

    abstract protected String generateEnableFeaturePreviewCode()

    String generateGradleProperties(boolean isRoot) {
        if (!isRoot && !config.compositeBuild) {
            return null
        }
        """
        org.gradle.jvmargs=-Xms${config.daemonMemory} -Xmx${config.daemonMemory} -Dfile.encoding=UTF-8
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

    String generatePomXML(Integer subProjectNumber, DependencyTree dependencyTree) {
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
                subProjectDependencies = subProjectNumbers.collect { convertToPomDependency("org.gradle.test.performance:project$it:1.0") }.join("")
            }
            body += """
            <dependencies>
                ${config.externalApiDependencies.values().collect { convertToPomDependency(it) }.join("")}
                ${config.externalImplementationDependencies.values().collect { convertToPomDependency(it) }.join("")}
                ${convertToPomDependency('junit:junit:4.13', 'test')}
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

    String generatePerformanceScenarios(boolean isRoot) {
        if (isRoot) {
            def fileToChange = config.fileToChangeByScenario['assemble']
            """
                nonAbiChange {
                  tasks = ["assemble"]
                  apply-non-abi-change-to = "${fileToChange}"
                  maven {
                    targets = ["clean", "package", "-Dmaven.test.skip=true", "-T", "4"]
                  }
                  bazel {
                    targets = ["build", "//..."]
                  }
                }

                abiChange {
                  tasks = ["assemble"]
                  apply-abi-change-to = "${fileToChange}"
                  maven {
                    targets = ["clean", "package", "-Dmaven.test.skip=true", "-T", "4"]
                  }
                  bazel {
                    targets = ["build", "//..."]
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
                    targets = ["clean", "package", "-T", "4"]
                  }
                }

                cleanBuildCached {
                  tasks = ["clean", "build"]
                  maven {
                    targets = ["clean", "package", "-T", "4"]
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

    String generateProductionClassFile(Integer subProjectNumber, int classNumber, DependencyTree dependencyTree) {
        def properties = ''
        def ownPackageName = packageName(classNumber, subProjectNumber)
        Set<String> imports = [] as HashSet
        def children = dependencyTree.getTransitiveChildClassIds(classNumber)
        (0..Math.max(propertyCount, children.size()) - 1).each {
            def propertyType
            int childNumber
            def initialization
            if (it < children.size()) {
                childNumber = children.get(it)
                propertyType = "Production${childNumber}"
                def childPackageName = packageName(childNumber, config.subProjects == 0 ? null : dependencyTree.getProjectIdForClass(childNumber))
                if (childPackageName != ownPackageName) {
                    imports.add("import ${childPackageName}.Production${childNumber};".toString())
                }
                initialization = ""
            } else if (it == 0 && subProjectNumber % 4 == 0 && subProjectNumber != 0) {
                imports.add("import org.gradle.test.performance.compilationavoidanceexperiment.project0.p0.Production0;")
                propertyType = "Production0"
                initialization = " = new " + propertyType + "()"
            } else if (it == 1 && subProjectNumber % 4 == 0 && subProjectNumber != 0) {
                propertyType = "String"
                initialization = " = property0.getProperty1()"
            } else {
                propertyType = "String"
                initialization = ""
            }

            properties += """
            private $propertyType property$it$initialization;

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

        ${imports.join("\n")}

        public class Production$classNumber {
            $properties
        }
        """
    }

    String generateTestClassFile(Integer subProjectNumber, int classNumber, DependencyTree dependencyTree) {
        def testMethods = ""
        def ownPackageName = packageName(classNumber, subProjectNumber)
        Set<String> imports = [] as HashSet
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
                    imports.add("import ${childPackageName}.Production${childNumber};".toString())
                }
            } else if (it == 0 && subProjectNumber % 4 == 0 && subProjectNumber != 0) {
                propertyType = "Production0"
                propertyValue = "new Production0()"
                imports.add("import org.gradle.test.performance.compilationavoidanceexperiment.project0.p0.Production0;")
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

        ${imports.join("\\n")}

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

    protected final int getPropertyCount() {
        Math.ceil((double) config.minLinesOfCodePerSourceFile / 10)
    }

    protected final String decideOnJavaPlugin(String plugin, Boolean projectHasParents) {
        if (plugin.contains('java')) {
            if (projectHasParents) {
                return """
                    ${pluginBlockApply("java-library")}
                """
            } else {
                return pluginBlockApply("java")
            }
        }
        return pluginBlockApply(plugin)
    }

    private dependenciesBlock(String api, String implementation, String testImplementation, Integer subProjectNumber, DependencyTree dependencyTree) {
        def hasParent = dependencyTree.hasParentProject(subProjectNumber)
        def subProjectNumbers = dependencyTree.getChildProjectIds(subProjectNumber)
        def subProjectDependencies = []
        if (subProjectNumbers?.size() > 0) {
            def abiProjectNumber = subProjectNumbers.get(DependencyTree.API_DEPENDENCY_INDEX)
            subProjectDependencies.addAll(subProjectNumbers.collect {
                it == abiProjectNumber ? projectDependencyDeclaration(hasParent ? api : implementation, abiProjectNumber) : projectDependencyDeclaration(implementation, it)
            })
        }
        if (subProjectNumber % 4 == 0 && subProjectNumber != 0) {
            subProjectDependencies.add(projectDependencyDeclaration("implementation", 0))
        }
        def block = """
                    ${config.externalApiDependencies.keySet().collect { versionCatalogDependencyDeclaration(hasParent ? api : implementation, it) }.join("\n            ")}
                    ${config.externalImplementationDependencies.keySet().collect { versionCatalogDependencyDeclaration(implementation, it) }.join("\n            ")}
                    ${versionCatalogDependencyDeclaration(testImplementation, config.useTestNG ? 'testng' : 'junit')}

                    ${subProjectDependencies.join("\n            ")}
        """
        return """
            dependencies {
                $block
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


    protected abstract String tasksConfiguration()

    protected abstract String pluginBlockApply(String plugin)

    protected abstract String createTaskThatDependsOnAllIncludedBuildsTaskWithSameName(String taskName)


    protected abstract String versionCatalogDependencyDeclaration(String configuration, String alias)

    protected abstract String projectDependencyDeclaration(String configuration, int projectNumber)

    protected final String dependency(int projectNumber) {
        if (config.compositeBuild) {
            return "\"org.gradle.test.performance:project${projectNumber}:1.0\""
        }
        return "project(\":project${projectNumber}\")"
    }
}
