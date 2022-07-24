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

package org.gradle.api.internal.project.ant

import org.apache.tools.ant.BuildEvent
import org.apache.tools.ant.Project
import org.apache.tools.ant.Task
import org.gradle.api.AntBuilder.AntMessagePriority
import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.events.CategorisedOutputEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import spock.lang.Specification


class AntLoggingAdapterTest extends Specification {
    OutputEventListener listener = Mock(OutputEventListener)
    AntLoggingAdapter antLoggingAdapter = new AntLoggingAdapter()
    ConfigureLogging logging = new ConfigureLogging(listener);

    def setup() {
        logging.attachListener()
    }

    def cleanup() {
        logging.resetLogging()
    }

    def "ant message priority #antPriority is mapped to #gradleLogLevel when lifecycle logging is set to #lifecyLevel"() {
        when:
        antLoggingAdapter.setLifecycleLogLevel(lifecyLevel)
        antLoggingAdapter.messageLogged(buildEvent(antPriority))

        then:
        1 * listener.onOutput({it.logLevel == gradleLogLevel && isCategorizedAsAntLoggingAdapter(it)})

        where:
        lifecyLevel                | antPriority         | gradleLogLevel
        null                       | Project.MSG_DEBUG   | LogLevel.DEBUG
        null                       | Project.MSG_VERBOSE | LogLevel.DEBUG
        null                       | Project.MSG_INFO    | LogLevel.INFO
        null                       | Project.MSG_WARN    | LogLevel.WARN
        null                       | Project.MSG_ERR     | LogLevel.ERROR
        AntMessagePriority.DEBUG   | Project.MSG_DEBUG   | LogLevel.LIFECYCLE
        AntMessagePriority.DEBUG   | Project.MSG_VERBOSE | LogLevel.LIFECYCLE
        AntMessagePriority.DEBUG   | Project.MSG_INFO    | LogLevel.LIFECYCLE
        AntMessagePriority.DEBUG   | Project.MSG_WARN    | LogLevel.WARN
        AntMessagePriority.DEBUG   | Project.MSG_ERR     | LogLevel.ERROR
        AntMessagePriority.VERBOSE | Project.MSG_DEBUG   | LogLevel.DEBUG
        AntMessagePriority.VERBOSE | Project.MSG_VERBOSE | LogLevel.LIFECYCLE
        AntMessagePriority.VERBOSE | Project.MSG_INFO    | LogLevel.LIFECYCLE
        AntMessagePriority.VERBOSE | Project.MSG_WARN    | LogLevel.WARN
        AntMessagePriority.VERBOSE | Project.MSG_ERR     | LogLevel.ERROR
        AntMessagePriority.INFO    | Project.MSG_DEBUG   | LogLevel.DEBUG
        AntMessagePriority.INFO    | Project.MSG_VERBOSE | LogLevel.DEBUG
        AntMessagePriority.INFO    | Project.MSG_INFO    | LogLevel.LIFECYCLE
        AntMessagePriority.INFO    | Project.MSG_WARN    | LogLevel.WARN
        AntMessagePriority.INFO    | Project.MSG_ERR     | LogLevel.ERROR
        AntMessagePriority.WARN    | Project.MSG_DEBUG   | LogLevel.DEBUG
        AntMessagePriority.WARN    | Project.MSG_VERBOSE | LogLevel.DEBUG
        AntMessagePriority.WARN    | Project.MSG_INFO    | LogLevel.INFO
        AntMessagePriority.WARN    | Project.MSG_WARN    | LogLevel.WARN
        AntMessagePriority.WARN    | Project.MSG_ERR     | LogLevel.ERROR
        AntMessagePriority.ERROR   | Project.MSG_DEBUG   | LogLevel.DEBUG
        AntMessagePriority.ERROR   | Project.MSG_VERBOSE | LogLevel.DEBUG
        AntMessagePriority.ERROR   | Project.MSG_INFO    | LogLevel.INFO
        AntMessagePriority.ERROR   | Project.MSG_WARN    | LogLevel.INFO
        AntMessagePriority.ERROR   | Project.MSG_ERR     | LogLevel.ERROR
    }

    BuildEvent buildEvent(antPriority) {
        return Stub(BuildEvent) {
            _ * getPriority() >> antPriority
            _ * getTask() >> Stub(Task)
        }
    }

    static boolean isCategorizedAsAntLoggingAdapter(OutputEvent event) {
        return event instanceof CategorisedOutputEvent &&
            (event as CategorisedOutputEvent).category == AntLoggingAdapter.canonicalName
    }
}
