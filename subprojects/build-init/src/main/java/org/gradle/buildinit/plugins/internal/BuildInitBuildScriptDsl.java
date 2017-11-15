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
package org.gradle.buildinit.plugins.internal;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.wrapper.Wrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public enum BuildInitBuildScriptDsl {

    GROOVY(".gradle", Wrapper.DistributionType.BIN),
    KOTLIN(".gradle.kts", Wrapper.DistributionType.ALL);

    private final String fileExtension;
    private final Wrapper.DistributionType wrapperDistributionType;

    BuildInitBuildScriptDsl(String fileExtension, Wrapper.DistributionType wrapperDistributionType) {
        this.fileExtension = fileExtension;
        this.wrapperDistributionType = wrapperDistributionType;
    }

    public static BuildInitBuildScriptDsl fromName(@Nullable String name) {
        if (name == null) {
            return GROOVY;
        }
        for (BuildInitBuildScriptDsl language : values()) {
            if (language.getId().equals(name)) {
                return language;
            }
        }
        throw new GradleException("The requested build script language '" + name + "' is not supported.");
    }

    public static List<String> listSupported() {
        List<String> result = new ArrayList<String>();
        for (BuildInitBuildScriptDsl language : values()) {
            result.add(language.getId());
        }
        return result;
    }

    public String getId() {
        return name().toLowerCase();
    }

    public Wrapper.DistributionType getWrapperDistributionType() {
        return wrapperDistributionType;
    }

    public String fileNameFor(String filenameWithoutExtension) {
        return filenameWithoutExtension + fileExtension;
    }
}
