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

import spock.lang.Specification

class DependencyTreeTest extends Specification {

    def tree = new DependencyTree()

    def "arranges classes on 2 levels"() {
        //    3
        //  / | \
        //  0 1 2

        given:
        def classCount = 4

        when:
        tree.calculateClassDependencies(0, 0, classCount)

        then:
        tree.getTransitiveChildClassIds(3) == [0, 1, 2]
    }

    def "arranges classes on many levels"() {
        //           12
        //    /      |     \
        //    3      7     11
        //  / | \  / | \  / | \
        //  0 1 2  4 5 6  8 9 10

        given:
        def maxLevel = 4
        def classCount = amount(3, maxLevel) + 1
        when:
        tree.calculateClassDependencies(0, 0, classCount)

        then:
        def level4Class = classCount - 1
        tree.getTransitiveChildClassIds(level4Class).size() == 3
        def level3Class = tree.getTransitiveChildClassIds(level4Class).get(0)
        tree.getTransitiveChildClassIds(level3Class).size() == 3
        def level2Class = tree.getTransitiveChildClassIds(level3Class).get(0)
        tree.getTransitiveChildClassIds(level2Class).size() == 3
        def level1Class = tree.getTransitiveChildClassIds(level2Class).get(0)
        tree.getTransitiveChildClassIds(level1Class).size() == 3
        def level0Class = tree.getTransitiveChildClassIds(level1Class).get(0)
        tree.getTransitiveChildClassIds(level0Class).size() == 0
    }

    def "arranges projects on many levels"() {
        //              p12
        //     /         |        \
        //    p3        p7        p11
        //   / | \     / | \     / | \
        //  p0 p1 p2  p4 p5 p6  p8 p9 p10

        given:
        def maxLevel = 4
        def projectCount = amount(3, maxLevel) + 1
        when:

        for (int i = 0; i < projectCount; i++) {
            //for each project, we need at least one class
            tree.calculateClassDependencies(i, i, i + 1)
        }
        tree.calculateProjectDependencies()

        then:
        def level4Project = projectCount - 1
        tree.getChildProjectIds(level4Project).size() == 3
        def level3Project = tree.getChildProjectIds(level4Project).get(0)
        tree.getChildProjectIds(level3Project).size() == 3
        def level2Project = tree.getChildProjectIds(level3Project).get(0)
        tree.getChildProjectIds(level2Project).size() == 3
        def level1Project = tree.getChildProjectIds(level2Project).get(0)
        tree.getChildProjectIds(level1Project).size() == 3
        def level0Project = tree.getChildProjectIds(level1Project).get(0)
        tree.getChildProjectIds(level0Project).size() == 0
    }

    def "adds dependencies between classes inside projects"() {
        //      (Project Level 0)          (Project Level 1)
        // Project0  Project1  Project2       Project 3
        //                                      51
        //    /----------------------------------|
        //    |        /-------------------------|
        //    |        |          /--------------|-
        //    12       25        38            / | \
        //  / | \    / | \      / | \         42 46 50
        //  3 7 11  16 20 24  29 33 37

        given:
        def maxClassLevel = 2
        def maxProjectLevel = 1
        def classPerProjectCount = amount(3, maxClassLevel) + 1
        def projectCount = amount(3, maxProjectLevel) + 1
        when:

        for (int i = 0; i < projectCount; i++) {
            tree.calculateClassDependencies(i, i * classPerProjectCount, ((i+1) * classPerProjectCount) - 1)
        }
        tree.calculateProjectDependencies()

        then:
        tree.getProjectIdForClass(51) == 3
        tree.getProjectIdForClass(12) == 0
        tree.getProjectIdForClass(25) == 1
        tree.getProjectIdForClass(38) == 2
        tree.getTransitiveChildClassIds(51) == [42, 46, 50, 12, 25, 38]
    }

    def "adds additional dependency to projects reachable through api dependency"() {
        //      (Project Level 0)                 (Project Level 1)      (Project Level 2)
        // Project0  Project4  Project8    Project3  Project7 Project11     Project 12
        //                                                                       168
        //                                     /--------------api-----------------|
        //                                     |        /---------impl------------|
        //                                     |        |          /---impl-------|--
        //                                     51      103       155           /  |  \
        //     /--------------api--------------|        |         |          159 163 167
        //     |        /------------api----------------|         |
        //     |        |         /--------api--------------------|
        //    12       46        116

        given:
        def maxClassLevel = 2
        def maxProjectLevel = 2
        def classPerProjectCount = amount(3, maxClassLevel) + 1
        def projectCount = amount(3, maxProjectLevel) + 1
        when:

        for (int i = 0; i < projectCount; i++) {
            tree.calculateClassDependencies(i, i * classPerProjectCount, ((i+1) * classPerProjectCount) - 1)
        }
        tree.calculateProjectDependencies()

        then:
        tree.getTransitiveChildClassIds(168) == [159, 163, 167, 51, 103, 155, 12, 64, 116]
    }

    private static int amount(int count, int level) {
        if (level == 0) {
            return 0
        }
        Math.pow(count, level) + amount(count, level - 1)
    }
}
