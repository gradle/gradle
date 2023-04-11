/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.enterprise.impl

import org.gradle.configurationcache.InputTrackingState
import spock.lang.Specification

import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException

class DefaultGradleEnterprisePluginBackgroundJobExecutorsTest extends Specification {
    DefaultGradleEnterprisePluginBackgroundJobExecutors jobExecutors

    void setup() {
        jobExecutors = new DefaultGradleEnterprisePluginBackgroundJobExecutors(new InputTrackingState())
    }

    void cleanup() {
        jobExecutors.stop()
    }

    def "background job is executed"() {
        when:
        def task = new FutureTask<>(() -> 1)
        jobExecutors.userJobExecutor.execute(task)

        then:
        task.get() == 1
    }

    def "background job is rejected if submitted after shutdown"() {
        given:
        jobExecutors.stop()

        when:
        jobExecutors.userJobExecutor.execute {}

        then:
        thrown RejectedExecutionException
    }

    def "isInBackground returns proper status from inside the job"() {
        when:
        def task = new FutureTask<>(jobExecutors::isInBackground)
        jobExecutors.userJobExecutor.execute(task)

        then:
        task.get() == true
    }

    def "isInBackground returns proper status from outside the job"() {
        expect:
        !jobExecutors.isInBackground()
    }
}
