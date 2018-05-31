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

package org.gradle.play.plugins.ide

import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.app.PlayMultiProject
import org.gradle.play.internal.platform.PlayMajorVersion
import org.gradle.util.VersionNumber

class PlayIdeaPluginMultiprojectIntegrationTest extends PlayIdeaPluginIntegrationTest {

    @Override
    PlayApp getPlayApp() {
        new PlayMultiProject(oldVersion: versionNumber < VersionNumber.parse('2.6.0'))
    }

    File getModuleFile() {
        file("primary/primary.iml")
    }

    @Override
    List<File> getIdeFiles() {
        return super.getIdeFiles() + ["${playApp.name}.iml", 'submodule/submodule.iml', 'javalibrary/javalibrary.iml']
    }

    String[] getSourcePaths() {
        ["public", "conf", "app", "build/src/play/binary/routesScalaSources"]
    }

    String[] getBuildTasks() {
        [":ideaModule",
         ":ideaProject",
         ":ideaWorkspace",
         ":idea",
         ":javalibrary:ideaModule",
         ":javalibrary:idea",
         ":primary:compilePlayBinaryPlayRoutes",
         ":primary:ideaModule",
         ":primary:idea",
         ":submodule:ideaModule",
         ":submodule:idea"]
    }

    int getExpectedScalaClasspathSize() {
        return PlayIdeaPluginBasicIntegrationTest.PLAY_VERSION_TO_CLASSPATH_SIZE[PlayMajorVersion.forPlayVersion(version.toString())]
    }
}
