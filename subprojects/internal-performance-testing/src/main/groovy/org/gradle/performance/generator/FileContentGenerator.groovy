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
        ${config.plugins.collect { "apply plugin: '$it'" }.join("\n        ") }
        
        String compilerMemory = getProperty('compilerMemory')
        String testRunnerMemory = getProperty('testRunnerMemory')
        int testForkEvery = getProperty('testForkEvery') as Integer

        repositories {
            ${config.repositories.join("\n            ")}
        }      
        
        configurations {
            compile.extendsFrom projectsConfiguration
        }

        dependencies {
            ${config.externalApiDependencies.collect { "api '$it'" }.join("\n            ")}
            ${config.externalImplementationDependencies.collect { "implementation '$it'" }.join("\n            ")}
            testImplementation '${config.useTestNG ? 'org.testng:testng:6.4' : 'junit:junit:4.12'}'

            ${subProjectNumber == null ? '' : dependencyTree.parentToChildrenNodeSets.get(subProjectNumber).collect { "${it % 9 == 0 ? 'api' : 'implementation'} project(':project$it')" }.join("\n            ")}
        }
        
        tasks.withType(JavaCompile) {
            options.fork = true
            options.incremental = true
            options.forkOptions.jvmArgs = ["-Xms\${compilerMemory}".toString(), "-Xmx\${compilerMemory}".toString()]
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
        org.gradle.parallel=${config.subProjects > 0}
        compilerMemory=${config.compilerMemory}
        testRunnerMemory=${config.testRunnerMemory}
        testForkEvery=${config.testForkEvery}
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
                def childPackageName = packageName(childNumber, dependencyTree.findNodeSet(childNumber))
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

    int getPropertyCount() {
        Math.ceil(config.linesOfCodePerSourceFile / 10)
    }

    def packageName(int classNumber, Integer subProjectNumber = null, String separator = '.') {
        def projectPackage = subProjectNumber == null ? "" : "${separator}project$subProjectNumber"
        def subPackage = ".p${(int) (classNumber / 20)}"
        "org${separator}gradle${separator}test${separator}performance${separator}${config.projectName.toLowerCase()}${projectPackage}$subPackage"
    }
}
