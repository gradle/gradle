/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugins.cpp
import org.gradle.api.Incubating
import org.gradle.plugins.cpp.internal.AbstractLinkerSpec
import org.gradle.plugins.cpp.internal.LinkerSpec
import org.gradle.plugins.cpp.internal.SharedLibraryLinkerSpec

@Incubating
class LinkSharedLibrary extends AbstractLinkTask {
    @Override
    protected LinkerSpec createLinkerSpec() {
        return new Spec()
    }

    @Override
    Class<? extends LinkerSpec> getSpecType() {
        SharedLibraryLinkerSpec
    }

    public static class Spec extends AbstractLinkerSpec implements SharedLibraryLinkerSpec {
        private String installName;

        public String getInstallName() {
            return installName == null ? getOutputFile().getName() : installName;
        }

        public void setInstallName(String installName) {
            this.installName = installName;
        }

    }
}
