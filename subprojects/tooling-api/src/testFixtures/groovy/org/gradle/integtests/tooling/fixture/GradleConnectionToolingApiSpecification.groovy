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
import org.gradle.tooling.connection.ModelResult
import org.gradle.tooling.connection.ModelResults
import org.junit.runner.RunWith

@ToolingApiVersion(ToolingApiVersions.SUPPORTS_GRADLE_CONNECTION)
@TargetGradleVersion(">=1.2")
@RunWith(ToolingApiCompatibilitySuiteRunner)
abstract class GradleConnectionToolingApiSpecification extends AbstractToolingApiSpecification {

    public <T> T withConnection(@DelegatesTo(GradleConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.connection.GradleConnection"]) Closure<T> cl) {
        toolingApi.withGradleConnection(cl)
    }

    public ConfigurableOperation withModels(Class modelType, Closure cl = {}) {
        withConnection {
            def models = it.models(modelType)
            cl(models)
            new ConfigurableOperation(models).buildModel()
        }
    }

    public ConfigurableOperation withBuild(Closure cl = {}) {
        withConnection {
            def build = it.newBuild()
            cl(build)
            def out = new ConfigurableOperation(build)
            build.run()
            out
        }
    }

    public <T> ModelResults<T> loadToolingModels(Class<T> modelClass) {
        withConnection { connection -> connection.getModels(modelClass) }
    }

    // Transforms Iterable<ModelResult<T>> into Iterable<T>
    def unwrap(Iterable<ModelResult> modelResults) {
        modelResults.collect { it.model }
    }

    void assertFailure(Throwable failure, String... messages) {
        assert failure != null
        def causes = getCauses(failure)

        messages.each { message ->
            assert causes.contains(message)
        }
    }

    void assertFailureHasCause(Throwable failure, Class<Throwable> expectedType) {
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
