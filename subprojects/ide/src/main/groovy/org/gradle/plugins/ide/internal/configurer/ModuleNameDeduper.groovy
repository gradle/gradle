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
import org.gradle.api.Project

/**
 * Able to deduplicate names. Useful for IDE plugins to make sure module names (IDEA) or project names (Eclipse) are unique.
 * <p>
 */
class ModuleNameDeduper {

    void dedupe(Collection<DeduplicationTarget> targets) {
        List<String> givenEclipseProjectNames = targets.collect { it.moduleName }
        targets.each { target ->
            doDeduplication(givenEclipseProjectNames, targets, target)
        }
    }

    private String doDeduplication(List<String> givenModuleNames, Collection<DeduplicationTarget> targets, DeduplicationTarget target) {
        Project project = target.project
        if (project.parent == null) {
            return target.moduleName
        }
        String givenModuleName = target.moduleName
        boolean isDuplicate = givenModuleNames.findAll { givenModuleName == it }.size() > 1
        if (isDuplicate) {
            def parentTarget = targets.find { it.project.equals(project.parent) }
            if(parentTarget == null){
                parentTarget = new DeduplicationTarget(project: project.parent, moduleName: project.name, updateModuleName: { })
            }
            givenModuleName = doDeduplication(givenModuleNames, targets, parentTarget) + "-" + givenModuleName
        }

        def deduplicatedModuleName = removeDuplicateWords(givenModuleName)
        target.updateModuleName(deduplicatedModuleName)
        return deduplicatedModuleName;
    }

    private String removeDuplicateWords(String givenProjectName) {
        def wordlist = Lists.newArrayList(givenProjectName.split("-"))
        if (wordlist.size() > 2) {
            wordlist = wordlist.unique()
        }
        return wordlist.join("-")
    }

}
