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

package org.gradle.performance.util

import org.apache.commons.io.FileUtils
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListener
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.measure.MeasuredOperation

abstract class BaseJavaSourceFileUpdater extends BuildExperimentListenerAdapter {
    protected File projectDir
    protected List<File> projects
    protected List<File> projectsWithDependencies
    protected int projectCount
    protected Map<Integer, List<Integer>> dependencies
    protected Map<Integer, List<Integer>> reverseDependencies

    protected final Set<File> updatedFiles = []
    protected final SourceUpdateCardinality cardinality

    BaseJavaSourceFileUpdater(SourceUpdateCardinality cardinality = SourceUpdateCardinality.ONE_FILE) {
        this.cardinality = cardinality
    }

    protected static int perc(int perc, int total) {
        (int) Math.ceil(total * (double) perc / 100d)
    }

    protected static File backupFileFor(File file) {
        new File(file.parentFile, "${file.name}~")
    }

    protected void createBackupFor(File file) {
        updatedFiles << file
        FileUtils.copyFile(file, backupFileFor(file), true)
    }

    private void restoreFiles() {
        updatedFiles.each { File file ->
            restoreFile(file)
        }
        updatedFiles.clear()
    }

    protected static void restoreFile(File file) {
        println "Restoring $file"
        def backup = backupFileFor(file)
        FileUtils.copyFile(backup, file, true)
        backup.delete()
    }

    @Override
    void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
        if (projectDir != invocationInfo.projectDir) {
            projectDir = invocationInfo.projectDir

            projects = projectDir.listFiles().findAll { it.directory && it.name.startsWith('project') }.sort { it.name }
            projectCount = projects.size()

            // forcefully delete build directories (so that dirty local runs do not interfere with results)
            projects.each { pDir ->
                FileUtils.deleteDirectory(new File(pDir, 'build'))
            }

            // make sure execution is consistent independently of time
            Collections.shuffle(projects, new Random(31 * projectCount))
            // restore stale backup files in case a build was interrupted
            cleanup()

            // retrieve the dependencies in an exploitable form
            def generatedDepsMetadata = new File(projectDir, 'generated-deps.groovy')
            if (generatedDepsMetadata.exists()) {
                dependencies = new GroovyShell().evaluate(generatedDepsMetadata)
            } else {
                dependencies = [:]
            }
            reverseDependencies = [:].withDefault { [] }
            dependencies.each { p, deps ->
                deps.each {
                    reverseDependencies[it] << p
                }
            }
            projectsWithDependencies = projects.findAll { File it ->
                reverseDependencies[projectId(it)]
            }
        }
        if (!updatedFiles.isEmpty()) {
            restoreFiles()
        } else if (invocationInfo.phase != BuildExperimentRunner.Phase.WARMUP) {
            updateFiles()
        }
    }

    protected abstract void updateFiles()

    @Override
    void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
        if (invocationInfo.iterationNumber == invocationInfo.iterationMax) {
            println "Last iteration complete"
            cleanup()
        }
    }

    void cleanup() {
        if (projectDir?.exists()) {
            projectDir.eachFileRecurse { file ->
                if (file.name.endsWith('~')) {
                    restoreFile(new File(file.parentFile, file.name - '~'))
                }
            }
        }
        updatedFiles.clear()
    }

    protected Set<Integer> affectedProjects(File subproject) {
        Set dependents = reverseDependencies[projectId(subproject)] as Set
        Set transitiveClosure = new HashSet<>(dependents)
        int size = -1
        while (size != transitiveClosure.size()) {
            size = transitiveClosure.size()
            def newDeps = []
            transitiveClosure.each {
                newDeps.addAll(reverseDependencies[it])
            }
            transitiveClosure.addAll(newDeps)
        }
        println "Changes will transitively affect projects ${transitiveClosure.join(' ')}"
        dependents
    }

    protected static int projectId(File pDir) {
        Integer.valueOf(pDir.name - 'project')
    }
}
