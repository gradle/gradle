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
import spock.lang.Specification

import static java.nio.file.Files.createTempDirectory

class InjectGradleRunnerIntoFieldsInterceptor extends InjectGradleRunnerInterceptorBase {
    private shared

    InjectGradleRunnerIntoFieldsInterceptor(shared = true) {
        this.shared = shared
    }

    @Override
    public void intercept(IMethodInvocation invocation) {
        def instance = (shared ? invocation.sharedInstance : invocation.instance) as Specification

        def fieldsToFill = instance
            .specificationContext
            .currentSpec
            .fields
            .findAll { it.type == GradleRunner }
            .findAll { it.shared == shared }
            .findAll { !it.readValue(instance) }

        if(!fieldsToFill) {
            invocation.proceed()
            return
        }

        List<File> temporaryProjectDirs = []
        try {
            fieldsToFill.each {
                // determine the project dir to use
                def projectDirClosure = it.getAnnotation(ProjectDirProvider)?.value()

                File projectDir

                if(!projectDirClosure) {
                    projectDir = createTempDirectory('gradleRunner_').toFile()
                    temporaryProjectDirs << projectDir
                } else {
                    projectDir = determineProjectDir(projectDirClosure.newInstance(instance, instance)(), "field '$it.name'")
                }

                it.writeValue instance, prepareProjectDir(projectDir)
            }

            invocation.proceed()
        } finally {
            temporaryProjectDirs*.deleteDir()
        }
    }
}
