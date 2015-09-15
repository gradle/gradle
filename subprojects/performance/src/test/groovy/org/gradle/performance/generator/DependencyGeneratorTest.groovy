/*
 * Copyright 2015 the original author or authors.
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

import spock.lang.Specification


class DependencyGeneratorTest extends Specification {

    def "should generate project dependencies"() {
        given:
        def gcl = new GroovyClassLoader(this.getClass().getClassLoader())
        def srcFile = new File("src/dependency_generator.groovy")
        if(!srcFile.exists()) {
            srcFile = new File("subprojects/performance/src/dependency_generator.groovy")
        }
        gcl.parseClass(srcFile)
        def dependencyGenerator = gcl.loadClass("DependencyGenerator").newInstance()
        def numberOfProjects = 150
        dependencyGenerator.numberOfProjects = numberOfProjects
        when:
        def layerSizes = dependencyGenerator.calculateLayerSizes()
        println "layer sizes ${layerSizes}"
        then:
        layerSizes.size() == dependencyGenerator.numberOfLayers
        layerSizes.sum() == numberOfProjects
        when:
        def projectsInLayer = dependencyGenerator.splitProjectsInLayers(layerSizes)
        println "projects in each layer ${projectsInLayer}"
        then:
        projectsInLayer.size() == dependencyGenerator.numberOfLayers
        projectsInLayer.collect { it.size() }.sum() == numberOfProjects
        when:
        def projectDependencies = dependencyGenerator.createDependencies()
        projectDependencies.each { k, v -> println "$k $v" }
        then:
        projectDependencies.size() == numberOfProjects
        projectDependencies.values().any { it.size() > dependencyGenerator.numberOfDependencies } == false
        projectDependencies.values().any { it.size() == dependencyGenerator.numberOfDependencies }
        projectDependencies.keySet().every { it >= 1 && it <= numberOfProjects }
        projectDependencies.values().every { dependencyList -> dependencyList.every { it >= 1 && it <= numberOfProjects } }
    }
}
