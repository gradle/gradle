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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r16.CustomModel
import org.gradle.tooling.GradleConnectionException
import spock.lang.Issue

class NonSerializableExceptionCrossVersionSpec extends ToolingApiSpecification {
    def createToolingModelBuilderBuildFile(int exceptionNestingLevel) {
        file('build.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

allprojects {
    apply plugin: CustomPlugin
}

class CustomException extends Exception {
    Thread thread = Thread.currentThread() // non-serializable field
    CustomException(String msg) {
        super(msg)
    }
}

class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        throw ${'new RuntimeException(' * exceptionNestingLevel}new CustomException("Something went wrong in building the model")${')' * exceptionNestingLevel}
    }
}

class CustomPlugin implements Plugin<Project> {
    @Inject
    CustomPlugin(ToolingModelBuilderRegistry registry) {
        registry.register(new CustomBuilder())
    }

    public void apply(Project project) {
    }
}
"""
    }

    @Issue("GRADLE-3307")
    def "returns proper error message when non-serializable exception is thrown while building a custom build model"() {
        when:
        createToolingModelBuilderBuildFile(exceptionNestingLevel)
        withConnection { connection ->
            connection.model(CustomModel).get()
        }

        then:
        def e = thrown(GradleConnectionException)
        def exceptionString = getStackTraceAsString(e)
        !exceptionString.contains(NotSerializableException.getName())
        exceptionString.contains('Caused by: CustomException: Something went wrong in building the model')

        where:
        exceptionNestingLevel << (0..2).toList()
    }

    @Issue("GRADLE-3307")
    @TargetGradleVersion(">=3.2")
    def "returns proper error message when non-serializable RuntimeException is thrown while executing a broken build action"() {
        when:
        withConnection { connection ->
            connection.action(new RuntimeExceptionThrowingBrokenBuildAction()).run()
        }

        then:
        def e = thrown(GradleConnectionException)
        def exceptionString = getStackTraceAsString(e)
        !exceptionString.contains(NotSerializableException.getName())
        exceptionString.contains("Caused by: ${RuntimeExceptionThrowingBrokenBuildAction.CustomException.getName()}: $BrokenBuildAction.BUILD_ACTION_EXCEPTION_MESSAGE")
    }

    def createBuildFileForConfigurationPhaseCheck(int exceptionNestingLevel) {
        file('build.gradle') << """
class CustomException extends Exception {
    Thread thread = Thread.currentThread() // non-serializable field
    CustomException(String msg) {
        super(msg)
    }
}

throw ${'new RuntimeException(' * exceptionNestingLevel}new CustomException("Something went wrong in configuring the build")${')' * exceptionNestingLevel}
"""
    }

    def "returns proper error message when non-serializable exception is thrown in configuration phase"() {
        when:
        createBuildFileForConfigurationPhaseCheck(exceptionNestingLevel)
        withConnection { connection ->
            connection.newBuild().forTasks("assemble").run()
        }

        then:
        def e = thrown(GradleConnectionException)
        def exceptionString = getStackTraceAsString(e)
        !exceptionString.contains(NotSerializableException.getName())
        exceptionString.contains('Caused by: CustomException: Something went wrong in configuring the build')

        where:
        exceptionNestingLevel << (0..2).toList()
    }

    def createBuildFileForExecutionPhaseCheck(int exceptionNestingLevel) {
        file('build.gradle') << """
class CustomException extends Exception {
    Thread thread = Thread.currentThread() // non-serializable field
    CustomException(String msg) {
        super(msg)
    }
}

task run {
    doLast {
        throw ${'new RuntimeException(' * exceptionNestingLevel}new CustomException("Something went wrong in configuring the build")${')' * exceptionNestingLevel}
    }
}
"""
    }

    def "returns proper error message when non-serializable exception is thrown in execution phase"() {
        when:
        createBuildFileForExecutionPhaseCheck(exceptionNestingLevel)
        withConnection { connection ->
            connection.newBuild().forTasks("run").run()
        }

        then:
        def e = thrown(GradleConnectionException)
        def exceptionString = getStackTraceAsString(e)
        !exceptionString.contains(NotSerializableException.getName())
        exceptionString.contains('Caused by: CustomException: Something went wrong in configuring the build')

        where:
        exceptionNestingLevel << (0..2).toList()
    }


    private static String getStackTraceAsString(GradleConnectionException throwable) {
        StringWriter stringWriter = new StringWriter()
        throwable.printStackTrace(new PrintWriter(stringWriter))
        return stringWriter.toString()
    }
}
