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

package gradlebuild.modules.model


enum class License(val displayName: String) {
    Apache2("Apache 2.0"),
    BSD3("3-Clause BSD"),
    BSDStyle("BSD-style"),
    CDDL("CDDL"),
    EDL("Eclipse Distribution License 1.0"),
    EPL("Eclipse Public License 1.0"),
    LGPL21("LGPL 2.1"),
    MIT("MIT")
}
