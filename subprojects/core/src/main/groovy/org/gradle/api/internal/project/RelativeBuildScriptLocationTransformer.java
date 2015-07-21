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

package org.gradle.api.internal.project;


import com.google.common.base.Optional;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.RelativeFileNameTransformer;

import java.io.File;

public class RelativeBuildScriptLocationTransformer implements Transformer<Optional<String>, ProjectInternal> {
    @Override
    public Optional<String> transform(ProjectInternal project) {
        Transformer<String, File> stringFileTransformer = RelativeFileNameTransformer.from(project.getProjectDir());
        File file = project.getBuildScriptSource().getResource().getFile();
        if (null != file) {
            return Optional.of(stringFileTransformer.transform(file));
        }
        return Optional.absent();
    }
}
