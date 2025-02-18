/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.internal.ant.AntWorkParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.api.tasks.javadoc.GroovydocAccess;

public interface GroovydocParameters extends AntWorkParameters {
    ConfigurableFileCollection getSource();
    DirectoryProperty getDestinationDirectory();
    Property<Boolean> getUse();
    Property<Boolean> getNoTimestamp();
    Property<Boolean> getNoVersionStamp();

    Property<String> getWindowTitle();

    Property<String> getDocTitle();

    Property<String> getHeader();

    Property<String> getFooter();

    Property<String> getOverview();

    Property<GroovydocAccess> getAccess();

    SetProperty<Groovydoc.Link> getLinks();

    Property<Boolean> getIncludeAuthor();

    Property<Boolean> getProcessScripts();

    Property<Boolean> getIncludeMainForScripts();

    RegularFileProperty getTmpDir();
}
