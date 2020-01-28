/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest

class PlayApplicationPluginGoodBehaviourIntegrationTest extends WellBehavedPluginTest {

    def setup() {
        executer.expectDocumentedDeprecationWarning("The Play Application plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consider using the org.gradle.playframework-application plugin instead. See https://docs.gradle.org/current/userguide/play_plugin.html for more details.")
        executer.expectDocumentedDeprecationWarning("The Play Twirl plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consider using the org.gradle.playframework-twirl plugin instead. See https://docs.gradle.org/current/userguide/play_plugin.html for more details.")
        executer.expectDocumentedDeprecationWarning("The Play Routes plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consider using the org.gradle.playframework-routes plugin instead. See https://docs.gradle.org/current/userguide/play_plugin.html for more details.")
        executer.expectDocumentedDeprecationWarning("The java-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The scala-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    @Override
    String getMainTask() {
        // assemble task will fail because the binary is not 100% buildable without a repository
        return "playBinary"
    }

}
