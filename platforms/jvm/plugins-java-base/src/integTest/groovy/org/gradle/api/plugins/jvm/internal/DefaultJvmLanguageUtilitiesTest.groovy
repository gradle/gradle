/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal

import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.tasks.DefaultSourceSetOutput
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile

import static org.gradle.util.TestUtil.objectFactory

class DefaultJvmLanguageUtilitiesTest extends AbstractJvmPluginServicesTest {
    def "can register a new source directory set compiled with Java"() {
        def sourceSet = Mock(SourceSet)
        def sourceSetOutput = Mock(DefaultSourceSetOutput)
        def compileTask = Mock(JavaCompile)
        def compileTaskProvider = Stub(TaskProvider) {
            configure(_) >> { it[0].execute(compileTask) }
            get() >> compileTask
            flatMap(_) >> objectFactory().directoryProperty()
        }
        def allJava = Mock(SourceDirectorySet)
        def allSource = Mock(SourceDirectorySet)
        def classesTask = Mock(Task)
        def classesTaskProvider = Stub(TaskProvider) {
            configure(_) >> { it[0].execute(classesTask) }
            get() >> classesTask
        }

        when:
        jvmLanguageUtilities.registerJvmLanguageSourceDirectory(sourceSet, "mylang") {
            it.withDescription("my test language")
            it.compiledWithJava {
                it.targetCompatibility = '8'
            }
        }

        then:
        _ * sourceSet.getName() >> 'main'
        1 * sourceSet.getOutput() >> sourceSetOutput
        1 * sourceSetOutput.getGeneratedSourcesDirs() >> Stub(ConfigurableFileCollection)
        1 * sourceSetOutput.getClassesDirs() >> Stub(ConfigurableFileCollection)
        1 * tasks.register("compileMyLang", JavaCompile, _) >> compileTaskProvider
        1 * compileTask.getDestinationDirectory() >> objectFactory().directoryProperty()
        1 * sourceSet.getAllJava() >> allJava
        1 * sourceSet.getAllSource() >> allSource
        1 * allJava.source(_)
        1 * allSource.source(_)
        1 * tasks.named('classes') >> classesTaskProvider
        1 * classesTask.dependsOn(compileTaskProvider)
        1 * instanceGenerator.newInstance(DefaultJvmLanguageSourceDirectoryBuilder, _, project, sourceSet) >> new DefaultJvmLanguageSourceDirectoryBuilder("myLang", project, sourceSet)
        0 * _
    }
}
