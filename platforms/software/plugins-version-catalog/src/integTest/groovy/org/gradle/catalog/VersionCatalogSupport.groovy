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

package org.gradle.catalog

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

trait VersionCatalogSupport {
    void expectPlatformContents(File expectedTomlFile = file("build/version-catalog/libs.versions.toml"), String resultFile) {
        assert expectedTomlFile.exists()
        def generated = normaliseLineSeparators(expectedTomlFile.getText("utf-8"))
        def expected = normaliseLineSeparators(this.class.getResourceAsStream("${resultFile}.toml").getText("utf-8"))
        assert generated == expected
    }
}
