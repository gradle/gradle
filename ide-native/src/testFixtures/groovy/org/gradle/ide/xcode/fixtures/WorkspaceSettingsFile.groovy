/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.fixtures

import com.dd.plist.NSDictionary
import com.dd.plist.NSNumber
import com.dd.plist.PropertyListParser
import org.gradle.test.fixtures.file.TestFile

class WorkspaceSettingsFile {
    private static final String AUTO_CREATE_CONTEXTS_IF_NEEDED_KEY = "IDEWorkspaceSharedSettings_AutocreateContextsIfNeeded"
    final String name
    final File file
    final NSDictionary plist

    WorkspaceSettingsFile(TestFile file) {
        assert file.exists()
        this.file = file
        this.name = file.name.replace(".xcsettings", "")
        this.plist = PropertyListParser.parse(file)
    }

    boolean isAutoCreateContextsIfNeeded() {
        def value = plist.get(AUTO_CREATE_CONTEXTS_IF_NEEDED_KEY)
        if (value == null || !(value instanceof NSNumber) || !value.isBoolean()) {
            return true
        }

        return ((NSNumber)value).boolValue()
    }
}
