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
package org.gradle.api.plugins.quality

import org.gradle.api.Project

/**
 * Extension specifying options for
 * the PMD plugin.
 */
class PMDExtension {
    private Project project
    
    /**
     * Location of the html report
     */
    String reportsDirName
    
    /**
     * Location of the xml results
     */
    String resultsDirName
    
    /**
     * Paths to ruleset files.
     */
    Set<String> rulesets = [] as Set
    
    /**
     * Creates an extension instance tied
     * to the specified project.
     * 
     * Defaults the {@code resultsDirName} and 
     * {@code reportsDirName} to "pmd"
     * @param project
     */
    PMDExtension(Project project) {
        this.project = project
        this.reportsDirName = this.resultsDirName = 'pmd'
        rulesets.add(project.file('config/pmd/rulesets.xml'))
    }
    
    /**
    * Adds a ruleset file path to the set.
    * @param rulesets the ruleset path to add
    */
   void rulesets(String... rulesets) {
       this.rulesets.addAll(rulesets)
   }
    
    /**
     * Gets the directory to be used for Findbugs results. This is determined
     * using the {@code resultsDirName} property, evaluated relative to the
     * project's build directory.
     * @return the results dir for Findbugs
     */
    File getResultsDir() {
        return project.fileResolver.withBaseDir(project.buildDir).resolve(resultsDirName)
    }
    
    /**
     * Gets the directory to be used for Findbugs reports. This is determined
     * using the {@code resultsDirName} property, evaluated relative to the
     * project's build directory.
     * @return the reports  dir for Findbugs
     */
    File getReportsDir() {
        return project.fileResolver.withBaseDir(project.reportsDir).resolve(reportsDirName)
    }
}
