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
package org.gradle.language.base.internal.tasks;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.FileCopyAction;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.Input;
import org.gradle.language.base.internal.tasks.apigen.ApiStubGenerator;

import java.util.List;

public class ApiCreatorTask extends AbstractCopyTask {

    @Input
    private List<String> apiPackages;

    public List<String> getApiPackages() {
        return apiPackages;
    }

    public void setApiPackages(List<String> apiPackages) {
        this.apiPackages = apiPackages;
    }

    @Override
    protected CopyAction createCopyAction() {
        return new ApiFilteringAction(getFileResolver(), new ApiStubGenerator(ImmutableList.copyOf(apiPackages)));
    }

    private static final class ApiFilteringAction extends FileCopyAction {

        private final ApiStubGenerator stubGenerator;

        public ApiFilteringAction(FileResolver fileResolver, ApiStubGenerator apiStubGenerator) {
            super(fileResolver);
            stubGenerator = apiStubGenerator;
        }
    }
}
