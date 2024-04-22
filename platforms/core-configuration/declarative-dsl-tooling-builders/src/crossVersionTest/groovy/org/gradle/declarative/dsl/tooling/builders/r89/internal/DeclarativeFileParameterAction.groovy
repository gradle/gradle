/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.declarative.dsl.tooling.builders.r89.internal

import org.gradle.api.Action
import org.gradle.declarative.dsl.tooling.models.DeclarativeFile
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController

class DeclarativeFileParameterAction<T> implements BuildAction<T> {

    private final Class<T> modelClass;
    private final File declarativeFile;
    private final String declarativeFileContent;

    DeclarativeFileParameterAction(Class<T> modelClass, File declarativeFile, String declarativeFileContent) {
        this.modelClass = modelClass
        this.declarativeFile = declarativeFile
        this.declarativeFileContent = declarativeFileContent
    }

    @Override
    T execute(BuildController controller) {
        return controller.getModel(modelClass, DeclarativeFile.class, new Action<DeclarativeFile>() {
            @Override
            void execute(DeclarativeFile param) {
                param.setFile(declarativeFile)
                param.setContent(declarativeFileContent)
            }
        });
    }
}
