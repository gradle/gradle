/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.wrapper

import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.Task
import org.gradle.api.InvalidUserDataException
import org.gradle.wrapper.Install
import org.gradle.util.GradleUtil
import org.gradle.util.GradleVersion

/**
 * @author Hans Dockter
 */
class Wrapper extends ConventionTask {
    static final DEFAULT_URL_ROOT = 'http://dist.codehaus.org/gradle'

    static final ANT_SCRIPT_NAME = 'wrapper-build.xml'

    static final String ANT_SCRIPT = '''
<project name="gradle" default="execute" basedir="..">
    <target name="execute">
        <fail unless="gradle" message="You must set the gradle property via -D."/>
        <exec dir="${basedir}" executable="gradlew" resolveexecutable="true">
            <arg line="${gradle}"/>
        </exec>
    </target>
</project>
'''

    File scriptDestinationDir

    File gradleWrapperHomeParent

    String gradleVersion

    String urlRoot

    WrapperScriptGenerator wrapperScriptGenerator = new WrapperScriptGenerator()

    def self

    Wrapper(DefaultProject project, String name) {
        super(project, name)
        actions << this.&generate
        self = this
        scriptDestinationDir = project.projectDir
        gradleWrapperHomeParent = project.projectDir
        urlRoot = DEFAULT_URL_ROOT
    }

    private void generate(Task task) {
        if (self.scriptDestinationDir == null) {
            throw new InvalidUserDataException("The scriptDestinationDir property must be specified!")
        }
        File gradleWrapperHome = new File(self.gradleWrapperHomeParent, Install.WRAPPER_DIR)
        gradleWrapperHome.mkdirs()
        File gradleWrapperJar = new File(System.properties['gradle.home'] + '/lib',
                "$Install.WRAPPER_DIR-${new GradleVersion().version}.jar")
        task.project.ant {
            copy(file: gradleWrapperJar,
                tofile: new File(gradleWrapperHome, Install.WRAPPER_JAR),
                overwrite: true)
            new File(gradleWrapperHome, ANT_SCRIPT_NAME).write(ANT_SCRIPT)
        }
        wrapperScriptGenerator.generate(self.gradleVersion, self.urlRoot, self.scriptDestinationDir, project.ant)
    }
}
