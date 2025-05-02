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


package org.gradle.ide.xcode.internal.xcodeproj;

import com.dd.plist.NSDictionary;
import com.google.common.base.Preconditions;
import org.gradle.api.Named;

public class PBXBuildStyle extends PBXProjectItem implements Named {
    private final String name;
    private NSDictionary buildSettings;

    public PBXBuildStyle(String name) {
        this.name = Preconditions.checkNotNull(name);
        this.buildSettings = new NSDictionary();
    }

    @Override
    public String getName() {
        return name;
    }

    public NSDictionary getBuildSettings() {
        return buildSettings;
    }

    public void setBuildSettings(NSDictionary buildSettings) {
        this.buildSettings = buildSettings;
    }

    @Override
    public String isa() {
        return "PBXBuildStyle";
    }

    @Override
    public int stableHash() {
        return name.hashCode();
    }

    @Override
    public void serializeInto(XcodeprojSerializer s) {
        super.serializeInto(s);

        s.addField("name", name);
        s.addField("buildSettings", buildSettings);
    }
}
