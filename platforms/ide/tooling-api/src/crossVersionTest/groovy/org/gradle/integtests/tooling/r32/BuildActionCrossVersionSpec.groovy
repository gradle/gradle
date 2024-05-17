/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r32

import org.gradle.integtests.tooling.fixture.ClassLoaderFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile

class BuildActionCrossVersionSpec extends ToolingApiSpecification {
    @TargetGradleVersion(">=3.2")
    def "can use multiple action implementations with different classpath roots and loaded from same ClassLoader"() {
        settingsFile.text = 'rootProject.name = "not broken"'

        // Ensure daemon is reused
        toolingApi.requireIsolatedDaemons()

        // Copy each of the action classes into its own classes directory and load into a single ClassLoader
        // Actions also share some static state, to verify that actions are loaded into the same ClassLoader in the daemon
        def classesDir1 = file("actions/1")
        copyClassTo(Action1, classesDir1)
        copyClassTo(SharedActionStaticState, classesDir1)
        def classesDir2 = file("actions/2")
        copyClassTo(Action2, classesDir2)
        def cl = actionClassLoader(classesDir1, classesDir2)
        def action1 = cl.loadClass(Action1.name)
        assert action1 != Action1
        def action2 = cl.loadClass(Action2.name)
        assert action2 != Action2

        expect:
        def l1 = withConnection {
            it.action(action1.getConstructor().newInstance())
                .run()
        }
        l1 == ["not broken 1"]
        def l2 = withConnection {
            it.action(action2.getConstructor().newInstance())
                .run()
        }
        l2 == ["not broken 2"]
    }

    def "can use multiple action implementations with different classpath roots and loaded from different ClassLoaders"() {
        settingsFile.text = 'rootProject.name = "not broken"'

        // Ensure daemon is reused
        toolingApi.requireIsolatedDaemons()

        // Copy each of the action classes into its own classes directory and load into a single ClassLoader
        // Actions also share some static state, to verify that actions are loaded into the same ClassLoader in the daemon
        def classesDir1 = file("actions/1")
        copyClassTo(Action1, classesDir1)
        copyClassTo(SharedActionStaticState, classesDir1)
        def classesDir2 = file("actions/2")
        copyClassTo(Action2, classesDir2)
        copyClassTo(SharedActionStaticState, classesDir2)
        def cl1 = actionClassLoader(classesDir1)
        def action1 = cl1.loadClass(Action1.name)
        assert action1 != Action1
        def cl2 = actionClassLoader(classesDir2)
        def action2 = cl2.loadClass(Action2.name)
        assert action2 != Action2

        expect:
        def l1 = withConnection { c ->
            return c.action(action1.getConstructor().newInstance()).run()
        }
        l1 == ["not broken 1"]
        def l2 = withConnection { c ->
            return c.action(action2.getConstructor().newInstance()).run()
        }
        l2 == ["not broken 1"]
    }

    void copyClassTo(Class<?> cl, TestFile rootDir) {
        ClassLoaderFixture.copyClassTo(cl, rootDir)
    }

    ClassLoader actionClassLoader(TestFile... cp) {
        return ClassLoaderFixture.actionClassLoader(getClass().classLoader, cp)
    }
}
