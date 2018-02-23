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

import org.gradle.internal.Pair

/**
 * Generates a tree of dependencies between classes and sub-projects. It can be configured with
 * the number of projects and classes in each project by multiple calls to calculateClassDependencies().
 *
 * Reflects project dependencies in class dependencies and respects 'api' dependencies.
 * See DependencyTreeTest for details of the tree construction.
 */
class DependencyTree {

    /**
     * Index of project dependency (declaration order) that is treated as 'api' dependency
     */
    static final API_DEPENDENCY_INDEX = 0
    /**
     * Classes on which 'level' are used for cross-project dependencies
     */
    static final CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL = 2
    /**
     * How many sibling classes are put onto one 'level' in the class dependency tree
     */
    static final CLASSES_ON_LEVEL = 3
    /**
     * How many sibling project are put onto one 'level' in the project dependency tree
     */
    static final PROJECTS_ON_LEVEL = 3

    private List<Pair<Integer, Integer>> projectClassIdRanges = []

    private List<List<Integer>> projectLevelToProjectIds = []
    private List<List<Integer>> classLevelToClassIds = []

    private Map<Integer, List<Integer>> parentToChildClassIds = new HashMap<>()
    private Map<Integer, List<Integer>> parentToChildProjectIds = new HashMap<>()

    private Set<Integer> transitiveClassIds = new HashSet<>()

    def getChildProjectIds(Integer parentProjectId) {
        return parentToChildProjectIds.get(parentProjectId)
    }

    def hasParentProject(Integer childProjectId) {
        if (childProjectId == null) {
            return false
        }
        for (def childLists : parentToChildProjectIds.values()) {
            if (childLists.contains(childProjectId)) {
                return true
            }
        }
        false
    }

    def getProjectIdForClass(int classId) {
        for (int projectId = 0; projectId < projectClassIdRanges.size(); projectId++) {
            if (isInProject(classId, projectId)) {
                return projectId
            }
        }
    }

    def isInProject(int classId, int projectId) {
        if (projectClassIdRanges.size() <= projectId) {
            return false
        }
        def range = projectClassIdRanges.get(projectId)
        isInRange(classId, range.left, range.right)
    }

    def getTransitiveChildClassIds(int parentClassId) {
        List<Integer> result = []
        result.addAll(parentToChildClassIds.get(parentClassId))
        parentToChildClassIds.get(parentClassId).each {
            allVisibleChildClassIds(it, result)
        }
        return result
    }

    private allVisibleChildClassIds(int parentClassId, List<Integer> result) {
        def directChildrenIds = parentToChildClassIds.get(parentClassId)
        if (directChildrenIds && !directChildrenIds.isEmpty()) {
            directChildrenIds.each {
                if (transitiveClassIds.contains(it)) {
                    result.add(it)
                    allVisibleChildClassIds(it, result)
                }
            }
        }
    }

    DependencyTree calculateClassDependencies(int first, int last) {
        projectClassIdRanges.add(Pair.of(first, last))
        (first..last).each {
            placeOnLevel(it, 0, first, last, CLASSES_ON_LEVEL, classLevelToClassIds, parentToChildClassIds)
        }
        this
    }

    DependencyTree calculateProjectDependencies() {
        (0..projectClassIdRanges.size() - 1).each {
            placeOnLevel(it, 0, 0, Integer.MAX_VALUE, PROJECTS_ON_LEVEL, projectLevelToProjectIds, parentToChildProjectIds)
        }

        //for each class on CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL of the parent project, add a dependency to a class on the same level of each child project
        for (int parentProjectId = 0; parentProjectId < projectClassIdRanges.size(); parentProjectId++) {
            // the first project dependency is treated as `api` dependency and corresponding classes are marked as transient
            def projectIndex = 0
            for (int childProjectId : parentToChildProjectIds.get(parentProjectId)) {
                def parentClassIds = []
                if (classLevelToClassIds.size() > CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL) {
                    for (Integer parentClassInParentProject : classLevelToClassIds.get(CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL)) {
                        if (isInProject(parentClassInParentProject, parentProjectId)) {
                            parentClassIds.add(parentClassInParentProject)
                        }
                    }
                }
                if (!parentClassIds.empty) {
                    int classIndex = 0
                    for (Integer childClassInChildProject : classLevelToClassIds.get(CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL)) {
                        if (isInProject(childClassInChildProject, childProjectId)) {
                            parentToChildClassIds.get(parentClassIds.get(classIndex)).add(childClassInChildProject)
                            if (projectIndex == API_DEPENDENCY_INDEX) {
                                transitiveClassIds.add(childClassInChildProject)
                            }
                            classIndex++
                        }
                    }
                }
                projectIndex++
            }
        }
        this
    }

    private placeOnLevel(int node, int level, int firstInRange, int lastInRange, nodesPerGroup, List<List<Integer>> levelList, Map<Integer, List<Integer>> parentMap) {
        if (levelAboveHasFreeSpot(level, firstInRange, lastInRange, nodesPerGroup, levelList)) {
            //a free spot on one of the level above
            placeOnLevel(node, level + 1, firstInRange, lastInRange, nodesPerGroup, levelList, parentMap)
        } else {
            addToLevel(levelList, level, node)
            List<Integer> lastGroupOnLevelBelow
            if (level > 0) {
                def nodesOnLevelBelow = getFromLevel(levelList, firstInRange, lastInRange, level - 1)
                lastGroupOnLevelBelow = new ArrayList<>(nodesOnLevelBelow.subList(nodesOnLevelBelow.size() - nodesPerGroup, nodesOnLevelBelow.size()))
            } else {
                lastGroupOnLevelBelow = []
            }
            parentMap.put(node, lastGroupOnLevelBelow)
        }
    }

    private levelAboveHasFreeSpot(int level, int firstInRange, int lastInRange, nodesPerGroup, List<List<Integer>> levelList) {
        int nodesOnCurrentLevel = getFromLevel(levelList, firstInRange, lastInRange, level).size()
        int fullGroupsOnCurrentLevel = (int) (nodesOnCurrentLevel / nodesPerGroup)
        int nodesOnLevelAbove = getFromLevel(levelList, firstInRange, lastInRange, level + 1).size()
        if (nodesOnCurrentLevel == 0 && nodesOnLevelAbove == 0) {
            return false
        }
        nodesOnLevelAbove < fullGroupsOnCurrentLevel || levelAboveHasFreeSpot(level + 1, firstInRange, lastInRange, nodesPerGroup, levelList)
    }

    private getFromLevel(List<List<Integer>> list, int firstNodeInSet, int lastNodeInSet, int level) {
        if (list.size() <= level) {
            return []
        }
        filterBySet(list.get(level), firstNodeInSet, lastNodeInSet)
    }

    private addToLevel(List<List<Integer>> list, int level, int node) {
        if (list.size() == level) {
            list.add([])
        }
        list.get(level).add(node)
    }

    private filterBySet(List<Integer> list, int firstNodeInSet, int lastNodeInSet) {
        def filtered = []
        for (Integer node : list) {
            if (isInRange(node, firstNodeInSet, lastNodeInSet)) {
                filtered.add(node)
            }
        }
        return filtered
    }

    private isInRange(int id, int first, int last) {
        first <= id && id <= last
    }

}
