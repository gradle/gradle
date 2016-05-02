/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.internal.SettingsInternal;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.initialization.SettingsScript;

public class SettingScriptTarget extends DefaultScriptTarget {
    public SettingScriptTarget(SettingsInternal target) {
        super(target);
    }

    @Override
    public String getId() {
        return "settings";
    }

    @Override
    public Class<? extends BasicScript> getScriptClass() {
        return SettingsScript.class;
    }
}

