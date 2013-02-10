/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.distribution.internal;

import groovy.lang.Closure;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopySpecImpl;
import org.gradle.util.ConfigureUtil;

/**
 * Allow user to declare a distribution.
 *
 * @author scogneau
 */
public class DefaultDistribution implements Distribution {

    private final String name;

    private String baseName;

    private CopySpec contents;

    public DefaultDistribution(String name, String projectName, FileResolver fileResolver) {
        this.name = name;
        this.baseName = name == MAIN_DISTRIBUTION_NAME ? projectName : String.format("%s-%s", projectName, name);
        this.contents = new CopySpecImpl(fileResolver);
        contents.from("src/"+name+"/dist");
    }

    public String getName() {
        return name;
    }

    public String getBaseName() {
        return baseName;
    }

    public CopySpec getContents() {
        return contents;
    }

    public CopySpec contents(Closure contents) {
        ConfigureUtil.configure(contents, this.contents);
        return this.contents;
    }

}
