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

package org.gradle.tooling.internal.consumer

import org.gradle.testing.internal.util.Specification
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.tooling.internal.protocol.AfterBuildResult
import org.gradle.tooling.internal.protocol.AfterConfigurationResult
import org.gradle.tooling.internal.protocol.AfterLoadingResult
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException

class DefaultPhasedActionResultListenerTest extends Specification {

    def afterLoadingHandler = Mock(ResultHandler)
    def afterConfigurationHandler = Mock(ResultHandler)
    def afterBuildHandler = Mock(ResultHandler)
    def listener = new DefaultPhasedActionResultListener(afterLoadingHandler, afterConfigurationHandler, afterBuildHandler)

    def "right handler is called"() {
        when:
        listener.onResult(new AfterLoading<String>('result1', null))
        listener.onResult(new AfterConfiguration<String>('result2', null))
        listener.onResult(new AfterBuild<String>('result3', null))

        then:
        1 * afterLoadingHandler.onComplete('result1')
        1 * afterConfigurationHandler.onComplete('result2')
        1 * afterBuildHandler.onComplete('result3')
        0 * afterLoadingHandler.onFailure(_)
        0 * afterConfigurationHandler.onFailure(_)
        0 * afterBuildHandler.onFailure(_)
    }

    def "failures are adapted and sent to correct handlers"() {
        when:
        listener.onResult(new AfterLoading<Object>(null, new InternalUnsupportedBuildArgumentException('failure1')))
        listener.onResult(new AfterConfiguration<Object>(null, new InternalBuildActionFailureException(new RuntimeException('failure2'))))
        listener.onResult(new AfterBuild<Object>(null, new RuntimeException('failure3')))

        then:
        0 * afterLoadingHandler.onComplete(_)
        0 * afterConfigurationHandler.onComplete(_)
        0 * afterBuildHandler.onComplete(_)
        1 * afterLoadingHandler.onFailure({
            it instanceof UnsupportedBuildArgumentException &&
                it.message == 'Could not execute build action in phased action build.\nfailure1'
        })
        1 * afterConfigurationHandler.onFailure({
            it instanceof BuildActionFailureException &&
                it.message == 'The supplied build action failed with an exception.' &&
                it.cause instanceof RuntimeException &&
                it.cause.message == 'failure2'
        })
        1 * afterBuildHandler.onFailure({
            it instanceof GradleConnectionException &&
                it.message == 'Could not execute build action in phased action build.' &&
                it.cause instanceof RuntimeException &&
                it.cause.message == 'failure3'
        })
    }

    def "null handler is not called"() {
        def emptyListener = new DefaultPhasedActionResultListener(null, null, null)

        when:
        listener.onResult(new AfterLoading<String>('result', null))

        then:
        noExceptionThrown()
    }

    class Result<T> implements PhasedActionResult<T> {
        T result
        Throwable failure

        Result(T result, Throwable failure) {
            this.result = result
            this.failure = failure
        }

        @Override
        T getResult() {
            return result
        }

        @Override
        Throwable getFailure() {
            return failure
        }
    }

    class AfterLoading<T> extends Result<T> implements AfterLoadingResult<T> {
        AfterLoading(T result, Throwable failure) {
            super(result, failure)
        }
    }

    class AfterConfiguration<T> extends Result<T> implements AfterConfigurationResult<T> {
        AfterConfiguration(T result, Throwable failure) {
            super(result, failure)
        }
    }

    class AfterBuild<T> extends Result<T> implements AfterBuildResult<T> {
        AfterBuild(T result, Throwable failure) {
            super(result, failure)
        }
    }
}
