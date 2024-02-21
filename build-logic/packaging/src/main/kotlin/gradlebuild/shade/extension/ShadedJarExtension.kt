/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.shade.extension

import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.SetProperty


abstract class ShadedJarExtension(val shadedConfiguration: Configuration) {

    /**
     * Retain only those classes in the keep package hierarchies, plus any classes that are reachable from these classes.
     */
    abstract val keepPackages: SetProperty<String>

    /**
     * Do not rename classes in the unshaded package hierarchies. Always includes 'java'.
     */
    abstract val unshadedPackages: SetProperty<String>

    /**
     * Do not retain classes in the ignore packages hierarchies, unless reachable from some other retained class.
     */
    abstract val ignoredPackages: SetProperty<String>
}
