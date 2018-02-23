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

package org.gradle.api.internal.initialization.loadercache

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ClassLoaderLeakAvoidanceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireDaemon().requireIsolatedDaemons()
    }

    def "convention mapping does not leak old classloaders"() {
        given:
        def myTask = file("buildSrc/src/main/groovy/MyTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            
            class MyTask extends DefaultTask {
                static final byte[] MEMORY_HOG = new byte[70 * 1024 * 1024]
                
                @Input 
                String someProperty
                
                @TaskAction
                public void runAction0() {} 
            }
        """
        buildFile << """
            task myTask(type: MyTask) {
                conventionMapping.someProperty = {"Foo"}
            }
        """

        expect:
        for(int i = 0; i < 5; i++) {
            myTask.text = myTask.text.replace("runAction$i", "runAction${i + 1}")
            executer.withBuildJvmOpts("-Xmx256m", "-XX:+HeapDumpOnOutOfMemoryError")
            assert succeeds("myTask")
            sleep(1000)
        }
    }
}
