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


enum class License(val displayName: String, val url: String) {
    Apache2(
        "Apache License, Version 2.0",
        "https://www.apache.org/licenses/LICENSE-2.0"
    ),
    BSD3(
        "3-Clause BSD License",
        "https://opensource.org/licenses/BSD-3-Clause"
    ),
    BSDStyle(
        "BSD-style License",
        "https://opensource.org/licenses/BSD-2-Clause"
    ),
    CDDL(
        "CDDL",
        "https://opensource.org/licenses/CDDL-1.0"
    ),
    EDL(
        "Eclipse Distribution License 1.0",
        "https://www.eclipse.org/org/documents/edl-v10.php"
    ),
    EPL(
        "Eclipse Public License 1.0",
        "https://www.eclipse.org/legal/epl-v10.html"
    ),
    EPL2(
        "Eclipse Public License 2.0",
        "https://www.eclipse.org/legal/epl-v20.html"
    ),
    LGPL21(
        "LGPL 2.1",
        "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"
    ),
    MIT(
        "MIT License",
        "https://opensource.org/licenses/MIT"
    ),
    MPL2(
        "Mozilla Public License 2.0",
        "https://www.mozilla.org/en-US/MPL/2.0/"
    ),
}
