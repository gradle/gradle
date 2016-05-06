/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal.configurer

import com.google.common.collect.Lists
import groovy.transform.CompileStatic
import org.gradle.api.Project

/**
 * Able to deduplicate names. Useful for IDE plugins to make sure module names (IDEA) or project names (Eclipse) are unique.
 * <p>
 */
@CompileStatic
class ModuleNameDeduper {

    void dedupe(Collection<DeduplicationTarget> targets) {

        //init project to prefixproject mapping
        Map<Project, Project> projectToPrefixMap = [:]
        targets.each { target ->
            projectToPrefixMap[target.project] = target.project.parent
        }
        Map<String, String> originalProjectNames = (Map<String, String>) targets.inject([:]) { acc, value ->
            acc[value] = value.moduleName
            acc
        }

        for (List<String> projectNames = targets.collect { it.moduleName }; hasDuplicates(projectNames); projectNames = targets.collect { it.moduleName }) {
            doDedup(targets, projectToPrefixMap)
        }

        List<String> deduplicatedProjectNames = targets.collect { it.moduleName }
        targets.each { target ->
            def simplifiedProjectName = removeDuplicateWordsFromPrefix(target.moduleName, originalProjectNames.get(target))
            if (!deduplicatedProjectNames.contains(simplifiedProjectName)) {
                target.moduleName = simplifiedProjectName
            }
            target.updateModuleName.call(target.moduleName)
        }
    }

    boolean hasDuplicates(List<String> projectNames) {
        projectNames.size() != (projectNames as Set).size()
    }

    Set<String> duplicates(List<String> projectNames) {
        return projectNames.groupBy { it }.findAll { key, value -> value.size() > 1 }.keySet()
    }

    def doDedup(Collection<DeduplicationTarget> targets, Map<Project, Project> prefixMap) {
        def duplicateProjectNames = duplicates(targets.collect { it.moduleName })
        duplicateProjectNames.each { duplicateProjectName ->

            def targetsToDeduplicate = targets.findAll { it.moduleName == duplicateProjectName }
            def notYetDedupped = targetsToDeduplicate.findAll { !it.deduplicated }

            if (notYetDedupped.size() > 1) {
                notYetDedupped.each { dedupTarget(it, prefixMap) }
            } else {
                targetsToDeduplicate.findAll { !notYetDedupped.contains(it) }.each { dedupTarget(it, prefixMap) }

                if (targetsToDeduplicate.every { it.moduleName == duplicateProjectName }) {
                    notYetDedupped.each { dedupTarget(it, prefixMap) }
                }
            }
        }
    }

    def dedupTarget(DeduplicationTarget target, Map<Project, Project> prefixMap) {
        Project prefixProject = prefixMap.get(target.project)
        if (prefixProject != null) {
            target.moduleName = prefixProject.name + "-" + target.moduleName
            prefixMap.put(target.project, prefixProject.parent)
            target.deduplicated = true
        }
    }

    private String removeDuplicateWordsFromPrefix(String deduppedProjectName, String originalProjectName) {
        if (deduppedProjectName.equals(originalProjectName)) {
            return deduppedProjectName
        }

        String prefix = deduppedProjectName.substring(0, deduppedProjectName.lastIndexOf(originalProjectName))
        List<String> prefixWordList = Lists.newArrayList(prefix.split("-"))
        List<String> postfixWordList = Lists.newArrayList(originalProjectName.split("-"))
        if (postfixWordList.size() > 1) {
            prefixWordList.add(postfixWordList.head())
            postfixWordList = postfixWordList.tail()
        }

        List<String> words = (List<String>) prefixWordList.inject([] as List<String>) { List<String> words, String newWord ->
            if (words.isEmpty() || !words.last().equals(newWord)) {
                words.add(newWord)
            }
            words
        }
        words.addAll(postfixWordList)
        return words.join("-")
    }
}
