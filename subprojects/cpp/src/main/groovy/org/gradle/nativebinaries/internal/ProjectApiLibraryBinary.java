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

package org.gradle.nativebinaries.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.ApiLibraryBinary;
import org.gradle.nativebinaries.BuildType;
import org.gradle.nativebinaries.Flavor;
import org.gradle.nativebinaries.Library;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

// TODO:DAZ This is all a bit of a hack to ensure that an ApiLibraryBinary has no sources, but does have headers.
// ApiLibraryBinary really should have a different API to ProjectNativeBinary, since it's not _built_.
// TODO:DAZ Improve this
public class ProjectApiLibraryBinary extends AbstractProjectLibraryBinary implements ApiLibraryBinary {

    public ProjectApiLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform platform, BuildType buildType,
                                   DefaultBinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(library, flavor, toolChain, platform, buildType, namingScheme.withTypeString("LibraryApi"), resolver);
    }

    @Override
    public DomainObjectSet<LanguageSourceSet> getSource() {
        return new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);
    }

    public FileCollection getHeaderDirs() {
        return new AbstractFileCollection() {
            public String getDisplayName() {
                return String.format("Headers for %s", getName());
            }

            public Set<File> getFiles() {
                Set<File> headerDirs = new LinkedHashSet<File>();
                for (HeaderExportingSourceSet sourceSet : ProjectApiLibraryBinary.super.getSource().withType(HeaderExportingSourceSet.class)) {
                    headerDirs.addAll(sourceSet.getExportedHeaders().getSrcDirs());
                }
                return headerDirs;
            }
        };
    }

    public File getPrimaryOutput() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBuildable() {
        return false;
    }

    public FileCollection getLinkFiles() {
        return new SimpleFileCollection();
    }

    public FileCollection getRuntimeFiles() {
        return new SimpleFileCollection();
    }
}
