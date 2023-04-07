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
package org.gradle.buildinit.plugins.internal.modifiers;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import java.util.List;

public enum BuildInitDsl implements WithIdentifier {

    KOTLIN(".gradle.kts"),
    GROOVY(".gradle");

    private final String fileExtension;

    BuildInitDsl(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public static BuildInitDsl fromName(@Nullable String name) {
        if (name == null) {
            return KOTLIN;
        }
        for (BuildInitDsl language : values()) {
            if (language.getId().equals(name)) {
                return language;
            }
        }
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("The requested build script DSL '" + name + "' is not supported. Supported DSLs");
        formatter.startChildren();
        for (BuildInitDsl dsl : values()) {
            formatter.node("'" + dsl.getId() + "'");
        }
        formatter.endChildren();
        throw new GradleException(formatter.toString());
    }

    public static List<String> listSupported() {
        ImmutableList.Builder<String> supported = ImmutableList.builder();
        for (BuildInitDsl dsl : values()) {
            supported.add(dsl.getId());
        }
        return supported.build();
    }

    @Override
    public String getId() {
        return Names.idFor(this);
    }

    public String fileNameFor(String fileNameWithoutExtension) {
        return fileNameWithoutExtension + fileExtension;
    }

    @Override
    public String toString() {
        return StringUtils.capitalize(name().toLowerCase());
    }
}
