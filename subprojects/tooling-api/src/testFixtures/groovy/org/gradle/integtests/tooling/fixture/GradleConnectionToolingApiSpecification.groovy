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

package org.gradle.integtests.tooling.fixture

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.connection.GradleConnectionBuilder
import org.gradle.tooling.connection.ModelResult
import org.gradle.tooling.connection.ModelResults
import org.junit.runner.RunWith

@ToolingApiVersion(ToolingApiVersions.SUPPORTS_GRADLE_CONNECTION)
@TargetGradleVersion(">=1.2")
@RunWith(ToolingApiCompatibilitySuiteRunner)
class GradleConnectionToolingApiSpecification extends AbstractToolingApiSpecification {

    protected <T> T withGradleConnection(@DelegatesTo(GradleConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.connection.GradleConnection"]) Closure<T> cl = {}) {
        GradleConnectionBuilder connector = toolingApi.gradleConnectionBuilder()
        connector.forRootDirectory(projectDir)
        return toolingApi.withGradleConnection(connector, cl)
    }

    protected <T> ModelResults<T> getModelsWithGradleConnection(Class<T> modelType) {
        withGradleConnection { connection ->
            return connection.getModels(modelType)
        }
    }

    protected <T> List<T> getUnwrappedModelsWithGradleConnection(Class<T> modelType) {
        unwrap(getModelsWithGradleConnection(modelType))
    }

    protected <T> List<T> unwrap(Iterable<ModelResult<T>> modelResults) {
        modelResults.collect { it.model }
    }

    protected assertFailure(Throwable failure, String... messages) {
        assert failure != null
        def causes = getCauses(failure)

        messages.each { message ->
            assert causes.contains(message)
        }
    }

    protected assertFailureHasCause(Throwable failure, Class<Throwable> expectedType) {
        assert failure != null
        Throwable throwable = failure
        List causes = []
        while (throwable != null) {
            causes << throwable.getClass().getCanonicalName()
            throwable = throwable.cause
        }
        assert causes.contains(expectedType.getCanonicalName())
    }

    private static String getCauses(Throwable throwable) {
        def causes = '';
        while (throwable != null) {
            if (throwable.message != null) {
                causes += throwable.message
                causes += '\n'
            }
            throwable = throwable.cause
        }
        causes
    }
}
