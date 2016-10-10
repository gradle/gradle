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

import org.gradle.tooling.model.ModelResult
import org.gradle.tooling.model.ModelResults

@ToolingApiVersion(ToolingApiVersions.SUPPORTS_MULTI_MODEL)
@TargetGradleVersion(">=1.2")
class MultiModelToolingApiSpecification extends ToolingApiSpecification {

    protected <T> ModelResults<T> getModels(Class<T> modelType) {
        withConnection { connection ->
            return connection.getModels(modelType)
        }
    }

    protected <T> List<T> getUnwrappedModels(Class<T> modelType) {
        unwrap(getModels(modelType))
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
