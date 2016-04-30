/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.nativeplatform.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.nativeplatform.internal.DefaultLinkerSpec;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec;

/**
 * Links a binary shared library from object files and imported libraries.
 */
@Incubating
@ParallelizableTask
public class LinkSharedLibrary extends AbstractLinkTask {
    private String installName;

    @Input
    @Optional
    public String getInstallName() {
        return installName;
    }

    public void setInstallName(String installName) {
        this.installName = installName;
    }

    @Override
    protected LinkerSpec createLinkerSpec() {
        final Spec spec = new Spec();
        spec.setInstallName(getInstallName());
        return spec;
    }

    private static class Spec extends DefaultLinkerSpec implements SharedLibraryLinkerSpec {
        private String installName;

        public String getInstallName() {
            return installName;
        }

        public void setInstallName(String installName) {
            this.installName = installName;
        }
    }
}
