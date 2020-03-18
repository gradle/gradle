/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.locking

import org.gradle.internal.locking.LockFileReaderWriter
import org.gradle.test.fixtures.file.TestFile

class LockfileFixture {

    TestFile testDirectory

    def createBuildscriptLockfile(String configuration, List<String> modules, boolean unique = true) {
        internalCreateLockfile(configuration, modules, unique, true)
    }

    def createLockfile(String configuration, List<String> modules, boolean unique = true) {
        internalCreateLockfile(configuration, modules, unique, false)
    }

    static def createCustomLockfile(TestFile lockFile, String configuration, List<String> modules) {
        internalCreateLockfile(modules, configuration, lockFile)
    }

    private void internalCreateLockfile(String configuration, List<String> modules, boolean unique, boolean buildScript) {
        if (unique) {
            def fileName = buildScript ? LockFileReaderWriter.BUILD_SCRIPT_PREFIX + LockFileReaderWriter.UNIQUE_LOCKFILE_NAME : LockFileReaderWriter.UNIQUE_LOCKFILE_NAME
            def lockFile = testDirectory.file(fileName)
            internalCreateLockfile(modules, configuration, lockFile)
        } else {
            def fileName = buildScript ? LockFileReaderWriter.BUILD_SCRIPT_PREFIX + configuration : configuration
            createLegacyLockfile(fileName, modules)
        }
    }

    private static void internalCreateLockfile(List<String> modules, String configuration, TestFile lockFile) {
        def lines = new ArrayList(LockFileReaderWriter.LOCKFILE_HEADER_LIST)
        if (modules.isEmpty()) {
            lines.add("empty=$configuration")
        } else {
            lines.addAll modules.toSorted().collect({ "$it=$configuration".toString() })
            lines.add("empty=")
        }
        lockFile.writelns(lines)
    }

    private void createLegacyLockfile(String configurationName, List<String> modules) {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, "$configurationName$LockFileReaderWriter.FILE_SUFFIX")
        def lines = new ArrayList(LockFileReaderWriter.LOCKFILE_HEADER_LIST)
        lines.addAll modules
        lockFile.writelns(lines.sort())
    }

    void verifyBuildscriptLockfile(String configurationName, List<String> expectedModules, boolean unique = true) {
        internalVerifyLockfile([(configurationName): expectedModules], unique, true)
    }

    void verifyLockfile(String configurationName, List<String> expectedModules, boolean unique = true) {
        internalVerifyLockfile([(configurationName): expectedModules], unique, false)
    }

    void verifyLockfile(Map<String, List<String>> expected, boolean unique = true) {
        internalVerifyLockfile(expected, unique, false)
    }

    static void verifyCustomLockfile(TestFile lockFile, String configuration, List<String> expected) {
        internalVerifyLockFileWithFile(lockFile, [(configuration): expected])
    }

    private void internalVerifyLockfile(Map<String, List<String>> expected, boolean unique, boolean buildScript) {
        if (unique) {
            def fileName = buildScript ? LockFileReaderWriter.BUILD_SCRIPT_PREFIX + LockFileReaderWriter.UNIQUE_LOCKFILE_NAME : LockFileReaderWriter.UNIQUE_LOCKFILE_NAME
            def lockFile = testDirectory.file(fileName)
            internalVerifyLockFileWithFile(lockFile, expected)
        } else {
            expected.entrySet().each {
                def fileName = buildScript ? LockFileReaderWriter.BUILD_SCRIPT_PREFIX + it.key : it.key
                verifyLegacyLockfile(fileName, it.value)
            }
        }
    }

    private static void internalVerifyLockFileWithFile(TestFile lockFile, Map<String, List<String>> expected) {
        assert lockFile.exists()
        def lockedModules = []
        lockFile.eachLine { String line ->
            if (!line.startsWith('#')) {
                lockedModules << line
            }
        }

        List<String> emptyConfs = new ArrayList<>()
        Map<String, List<String>> modulesToConf = new TreeMap<>()
        expected.keySet().toSorted().each {
            def modules = expected.get(it)
            if (modules.isEmpty()) {
                emptyConfs.add(it)
            } else {
                for (String module : (modules)) {
                    modulesToConf.compute(module, { k, v ->
                        List<String> confs = v
                        if (confs == null) {
                            confs = new ArrayList<>()
                        }
                        confs.add(it)
                        return confs
                    })
                }
            }
        }
        List<String> entries = new ArrayList<>()
        entries.addAll(modulesToConf.entrySet().collect({ "${it.key}=${it.value.join(',')}".toString() }))
        entries.sort()
        entries.add('empty=' + emptyConfs.join(","))

        assert lockedModules == entries
    }

    private void verifyLegacyLockfile(String configurationName, List<String> expectedModules) {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, "$configurationName$LockFileReaderWriter.FILE_SUFFIX")
        assert lockFile.exists()
        def lockedModules = []
        lockFile.eachLine { String line ->
            if (!line.startsWith('#')) {
                lockedModules << line
            }
        }

        assert lockedModules as Set == expectedModules as Set
    }

    void expectLockStateMissing(String configurationName, boolean unique = true) {
        if (unique) {
            def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, LockFileReaderWriter.UNIQUE_LOCKFILE_NAME)
            if (lockFile.exists()) {
                assert !lockFile.text.contains(configurationName)
            } else {
                assert !lockFile.exists()
            }
        } else {
            expectLegacyMissing(configurationName)
        }
    }

    private void expectLegacyMissing(String configurationName) {
        def lockFile = testDirectory.file(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER, "$configurationName$LockFileReaderWriter.FILE_SUFFIX")
        assert !lockFile.exists()
    }
}
