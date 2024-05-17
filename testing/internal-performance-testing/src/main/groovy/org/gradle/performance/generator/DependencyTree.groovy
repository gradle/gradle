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

/**
 * Generates a tree of dependencies between classes and sub-projects. It can be configured with
 * the number of projects and classes in each project by multiple calls to calculateClassDependencies().
 *
 * Reflects project dependencies in class dependencies and respects 'api' dependencies.
 * See DependencyTreeTest for details of the tree construction.
 */
@CompileStatic
class DependencyTree {

    /**
     * Index of project dependency (declaration order) that is treated as 'api' dependency
     */
    static final int API_DEPENDENCY_INDEX = 0
    /**
     * Classes on which 'level' are used for cross-project dependencies
     */
    static final int CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL = 2
    /**
     * How many sibling classes are put onto one 'level' in the class dependency tree
     */
    static final int CLASSES_ON_LEVEL = 3
    /**
     * How many sibling project are put onto one 'level' in the project dependency tree
     */
    static final int PROJECTS_ON_LEVEL = 3

    private List<List<Integer>> projectDependencyTree = []
    private Map<Integer, List<List<Integer>>> classDependencyTrees = new HashMap<>()

    private Map<Integer, List<Integer>> parentToChildClassIds = new HashMap<>()
    private Map<Integer, List<Integer>> parentToChildProjectIds = new HashMap<>()
    private Map<Integer, Integer> classToproject = new HashMap<>()

    private Set<Integer> transitiveClassIds = new HashSet<>()

    List<Integer> getChildProjectIds(Integer parentProjectId) {
        return parentToChildProjectIds.get(parentProjectId)
    }

    boolean hasParentProject(Integer childProjectId) {
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

    Integer getProjectIdForClass(int classId) {
        classToproject.get(classId)
    }

    List<Integer> getTransitiveChildClassIds(int parentClassId) {
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

    DependencyTree calculateClassDependencies(int project, int first, int last) {
        (first..last).each {
            classToproject.put(it, project)
            placeNode(it, CLASSES_ON_LEVEL, classDependencyTrees.computeIfAbsent(project) {[]}, parentToChildClassIds)
        }
        this
    }

    DependencyTree calculateProjectDependencies() {
        (0..classDependencyTrees.size() - 1).each {
            placeNode(it, PROJECTS_ON_LEVEL, projectDependencyTree, parentToChildProjectIds)
        }

        //for each class on CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL of the parent project, add a dependency to a class on the same level of each child project
        for (int parentProjectId = 0; parentProjectId < classDependencyTrees.size(); parentProjectId++) {
            // the first project dependency is treated as `api` dependency and corresponding classes are marked as transient
            def projectIndex = 0
            for (int childProjectId : parentToChildProjectIds.get(parentProjectId)) {
                def parentClassIds = []
                if (classDependencyTrees.get(parentProjectId).size() > CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL) {
                    for (Integer parentClassInParentProject : classDependencyTrees.get(parentProjectId).get(CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL)) {
                        parentClassIds.add(parentClassInParentProject)
                    }
                }
                if (!parentClassIds.empty) {
                    int classIndex = 0
                    for (Integer childClassInChildProject : classDependencyTrees.get(childProjectId).get(CROSS_PROJECT_CLASS_DEPENDENCY_LEVEL)) {
                        parentToChildClassIds.get(parentClassIds.get(classIndex)).add(childClassInChildProject)
                        if (projectIndex == API_DEPENDENCY_INDEX) {
                            transitiveClassIds.add(childClassInChildProject)
                        }
                        classIndex++
                    }
                }
                projectIndex++
            }
        }
        this
    }

    private placeNode(int node,  int nodesPerGroup, List<List<Integer>> levelList, Map<Integer, List<Integer>> parentMap) {
        int freeLevel = findLowestFreeLevel(nodesPerGroup, levelList)
        addToLevel(levelList, freeLevel, node)
        List<Integer> lastGroupOnLevelBelow
        if (freeLevel > 0) {
            def nodesOnLevelBelow = getFromLevel(levelList, freeLevel - 1)
            lastGroupOnLevelBelow = new ArrayList<>(nodesOnLevelBelow.subList(nodesOnLevelBelow.size() - nodesPerGroup, nodesOnLevelBelow.size()))
        } else {
            lastGroupOnLevelBelow = []
        }
        parentMap.put(node, lastGroupOnLevelBelow)
    }

    private int findLowestFreeLevel(int nodesPerGroup, List<List<Integer>> levelList, int level = levelList.size()) {
        if (level == 0) {
            return level
        }
        int nodesOnCurrentLevel = getFromLevel(levelList, level).size()
        int nodesOnLevelBelow = getFromLevel(levelList, level - 1).size()
        int fullGroupsOnLevelBelow = nodesOnLevelBelow.intdiv(nodesPerGroup)
        if (nodesOnCurrentLevel < fullGroupsOnLevelBelow) {
            return level
        }
        return findLowestFreeLevel(nodesPerGroup, levelList, level - 1)
    }

    private List<Integer> getFromLevel(List<List<Integer>> list, int level) {
        if (list.size() <= level) {
            return []
        }
        list.get(level)
    }

    private addToLevel(List<List<Integer>> list, int level, int node) {
        if (list.size() == level) {
            list.add([])
        }
        list.get(level).add(node)
    }

}
