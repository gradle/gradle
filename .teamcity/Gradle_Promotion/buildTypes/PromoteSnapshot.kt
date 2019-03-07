/*
 * Copyright 2019 the original author or authors.
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

package Gradle_Promotion.buildTypes

open class PromoteSnapshot(branch: String, triggerName: String, init: PromoteSnapshot.() -> Unit = {}) : BasePromoteSnapshot(branch = branch, triggerName = triggerName, task = "promote${if (branch == "master") "" else branch.capitalize()}Nightly") {
    val capitalizedBranch = branch.capitalize()

    init {
        this.init()
    }
}
