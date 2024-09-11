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

package org.gradle.smoketests

import groovy.transform.SelfType
import org.gradle.util.GradleVersion

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations {

    void expectFilteredFileCollectionDeprecationWarning() {
        // This warning is emitted by the Kotlin Kapt plugin
        runner.expectLegacyDeprecationWarning(
            "The Configuration.fileCollection(Spec) method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Use Configuration.getIncoming().artifactView(Action) with a componentFilter instead. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecate_filtered_configuration_file_and_filecollection_methods"
        )
    }

}
