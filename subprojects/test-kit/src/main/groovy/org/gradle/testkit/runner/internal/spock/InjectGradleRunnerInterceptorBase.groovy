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
import org.spockframework.runtime.extension.IMethodInterceptor

abstract class InjectGradleRunnerInterceptorBase implements IMethodInterceptor {
    private static supportedProjectDirProviderTypes = ProjectDirProviderCategory.methods
        .findAll { it.name == 'get' }
        .findAll { it.parameterTypes.size() == 1 }
        .collect { it.parameterTypes.first() }

    File determineProjectDir(projectDirProvider, fieldOrParameterName) {
        if(!projectDirProvider) {
            throw new IllegalArgumentException("The project dir provider closure for the GradleRunner $fieldOrParameterName returned '$projectDirProvider'")
        }

        if(!supportedProjectDirProviderTypes.any { it.isAssignableFrom projectDirProvider.getClass() }) {
            throw new IllegalArgumentException("The project dir provider closure for the GradleRunner $fieldOrParameterName " +
                "returned an object of the unsupported type '${projectDirProvider.getClass().typeName}'\n" +
                "\tsupported types:\n\t\t${supportedProjectDirProviderTypes.typeName.sort().join('\n\t\t')}")
        }

        def projectDir
        use(ProjectDirProviderCategory) {
            projectDir = projectDirProvider.get()
        }

        if(!projectDir) {
            throw new IllegalArgumentException("The extracted directory from the project dir provider closure result for the GradleRunner $fieldOrParameterName is '$projectDir'")
        }

        projectDir
    }

    GradleRunner prepareProjectDir(File projectDir) {
        // create, configure and return the gradle runner instance
        GradleRunner
            .create()
            .forwardStdOutput(System.out.newWriter())
            .forwardStdError(System.err.newWriter())
            .withProjectDir(projectDir)
    }
}
