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

package org.gradle.testkit.runner.internal.spock

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.spock.ProjectDirProvider
import org.spockframework.runtime.extension.IMethodInvocation

import static java.nio.file.Files.createTempDirectory

class InjectGradleRunnerIntoParametersInterceptor extends InjectGradleRunnerInterceptorBase {
    @Override
    public void intercept(IMethodInvocation invocation) {
        // create a map of all GradleRunner parameters with their parameter index
        Map<Class<? extends Object>, Integer> parameters = [:]
        invocation.method.reflection.parameterTypes.eachWithIndex { parameter, i -> parameters << [(parameter): i] }
        parameters = parameters.findAll { it.key == GradleRunner }

        // enlarge arguments array if necessary
        def lastGradleRunnerParameterIndex = parameters*.value.max()
        lastGradleRunnerParameterIndex = lastGradleRunnerParameterIndex == null ? 0 : lastGradleRunnerParameterIndex + 1
        if(invocation.arguments.length < lastGradleRunnerParameterIndex) {
            def newArguments = new Object[lastGradleRunnerParameterIndex]
            System.arraycopy invocation.arguments, 0, newArguments, 0, invocation.arguments.length
            invocation.arguments = newArguments
        }

        // find all parameters to fill
        def parametersToFill = parameters.findAll { !invocation.arguments[it.value] }

        if(!parametersToFill) {
            invocation.proceed()
            return
        }

        def parameterAnnotations = invocation.method.reflection.parameterAnnotations

        List<File> temporaryProjectDirs = []
        try {
            parametersToFill.each { parameter, i ->
                // determine the project dir to use
                def projectDirClosure = parameterAnnotations[i].find { it instanceof ProjectDirProvider }?.value()

                File projectDir

                if(!projectDirClosure) {
                    projectDir = createTempDirectory('gradleRunner_').toFile()
                    temporaryProjectDirs << projectDir
                } else {
                    projectDir = determineProjectDir(projectDirClosure.newInstance(invocation.instance, invocation.instance)(), "parameter '${invocation.feature.parameterNames[i]}'")
                }

                invocation.arguments[i] = prepareProjectDir(projectDir)
            }

            invocation.proceed()
        } finally {
            temporaryProjectDirs*.deleteDir()
        }
    }
}
