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

package org.gradle.api.tasks

class LocalStateFixture {
    static String defineTaskWithLocalState(boolean useRuntimeApi, localStateFile = 'file("local-state.json")') {
        def action = """
            if (System.getProperty("assertLocalState")) {
                assert localStateFile.isFile()
            }
            if (System.getProperty("assertNoLocalState")) {
                assert !localStateFile.exists()
            }
            new File("build/output.txt").text = "Output"
            localStateFile = org.gradle.util.internal.DeferredUtil.unpackOrNull(localStateFile)
            if (localStateFile != null) {
                localStateFile.text = "['Some internal state']"
            }
        """
        if (useRuntimeApi) {
            """
                task customTask {
                    def localStateFile = $localStateFile
                    outputs.cacheIf { true }
                    outputs.dir("build")
                    localState.register(localStateFile)
                    doLast { $action }
                }
            """
        } else {
            """
                @CacheableTask
                class CustomTask extends DefaultTask {
                    @OutputDirectory File outputDir
                    @LocalState File localStateFile
                    @TaskAction run() {
                        $action
                    }
                }

                task customTask(type: CustomTask) {
                    outputDir = file("build")
                    localStateFile = $localStateFile
                }
            """
        }
    }
}
