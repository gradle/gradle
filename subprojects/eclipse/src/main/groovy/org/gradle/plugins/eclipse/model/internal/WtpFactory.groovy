/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.eclipse.model.internal

import org.apache.commons.io.FilenameUtils
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.eclipse.model.WbDependentModule
import org.gradle.plugins.eclipse.model.WbProperty
import org.gradle.plugins.eclipse.model.WbResource
import org.gradle.plugins.eclipse.model.Wtp
import org.gradle.plugins.eclipse.EclipseWtp

/**
 * @author Hans Dockter
 */
class WtpFactory {
    Wtp createWtp(EclipseWtp eclipseWtp) {
        File componentInputFile = eclipseWtp.orgEclipseWstCommonComponentInputFile
        File facetInputFile = eclipseWtp.orgEclipseWstCommonProjectFacetCoreInputFile
        FileReader componentReader = componentInputFile != null && componentInputFile.exists() ? new FileReader(componentInputFile) : null
        FileReader facetReader = facetInputFile != null && facetInputFile.exists() ? new FileReader(facetInputFile) : null
        List entries = getEntriesFromSourceSets(eclipseWtp.sourceSets, eclipseWtp.project)
        entries.addAll(eclipseWtp.resources)
        entries.addAll(eclipseWtp.properties)
        entries.addAll(getEntriesFromConfigurations(eclipseWtp))
        return new Wtp(eclipseWtp, entries, componentReader, facetReader)
    }

    List getEntriesFromSourceSets(def sourceSets, def project) {
        List entries = []
        sourceSets.each { SourceSet sourceSet ->
            entries.add(new WbProperty('java-output-path', PathUtil.normalizePath(project.relativePath(sourceSet.classesDir))))
            sourceSet.allSource.sourceTrees.each { SourceDirectorySet sourceDirectorySet ->
                sourceDirectorySet.srcDirs.each { dir ->
                    if (dir.isDirectory()) {
                        entries.add(new WbResource("/WEB-INF/classes", project.relativePath(dir)))
                    }
                }
            }
        }
        entries
    }

    List getEntriesFromConfigurations(EclipseWtp eclipseWtp) {
        (getProjectsDependencies(eclipseWtp) as List) + (getLibraries(eclipseWtp) as List)
    }

    protected Set getProjectsDependencies(EclipseWtp eclipseWtp) {
        return getDependencies(eclipseWtp.plusConfigurations, eclipseWtp.minusConfigurations, { it instanceof org.gradle.api.artifacts.ProjectDependency }).collect { projectDependency ->
            projectDependency.dependencyProject
        }.collect { dependencyProject ->
            new WbDependentModule("/WEB-INF/lib", "module:/resource/" + dependencyProject.name + "/" + dependencyProject.name)
        }
    }

    protected Set getLibraries(EclipseWtp eclipseWtp) {
        Set declaredDependencies = getDependencies(eclipseWtp.plusConfigurations, eclipseWtp.minusConfigurations,
                { it instanceof ExternalDependency})

        Set libFiles = eclipseWtp.project.configurations.detachedConfiguration((declaredDependencies as Dependency[])).files +
                getSelfResolvingFiles(getDependencies(eclipseWtp.plusConfigurations, eclipseWtp.minusConfigurations,
                        { it instanceof SelfResolvingDependency && !(it instanceof org.gradle.api.artifacts.ProjectDependency)}))

        libFiles.collect { file ->
            createWbDependentModuleEntry(file, eclipseWtp.variables)
        }
    }

    private def getSelfResolvingFiles(Collection dependencies) {
        dependencies.inject([] as LinkedHashSet) { result, SelfResolvingDependency selfResolvingDependency ->
            result.addAll(selfResolvingDependency.resolve())
            result
        }
    }

    WbDependentModule createWbDependentModuleEntry(File file, Map<String, File> variables) {
        def usedVariableEntry = variables.find { name, value -> file.canonicalPath.startsWith(value.canonicalPath) }
        String handleSnippet;
        if (usedVariableEntry) {
            handleSnippet = "var/$usedVariableEntry.key/${file.canonicalPath.substring(usedVariableEntry.value.canonicalPath.length())}"
        } else {
            handleSnippet = "lib/${file.canonicalPath}"
        }
        handleSnippet = FilenameUtils.separatorsToUnix(handleSnippet)
        return new WbDependentModule('/WEB-INF/lib', "module:/classpath/$handleSnippet")
    }

    private Set getDependencies(Set plusConfigurations, Set minusConfigurations, Closure filter) {
        Set declaredDependencies = new LinkedHashSet()
        plusConfigurations.each { configuration ->
            declaredDependencies.addAll(configuration.getAllDependencies().findAll(filter))
        }
        minusConfigurations.each { configuration ->
            configuration.getAllDependencies().findAll(filter).each { minusDep ->
                declaredDependencies.remove(minusDep)
            }
        }
        return declaredDependencies
    }
}
