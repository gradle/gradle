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
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.nativeplatform.internal.DefaultLinkerSpec;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Links a binary shared library from object files and imported libraries.
 */
@Incubating
public class LinkSharedLibrary extends AbstractLinkTask {
    private String installName;
    private final RegularFileProperty importLibrary = newOutputFile();

    public LinkSharedLibrary() {
        importLibrary.set(getProject().getLayout().getProjectDirectory().file(getProject().getProviders().provider(new Callable<String>() {
            @Override
            public String call() throws Exception {
                RegularFile binaryFile = getBinaryFile().getOrNull();
                if (binaryFile == null) {
                    return null;
                }
                NativeToolChainInternal toolChain = (NativeToolChainInternal) getToolChain();
                NativePlatformInternal targetPlatform = (NativePlatformInternal) getTargetPlatform();
                if (toolChain == null || targetPlatform == null) {
                    return null;
                }
                PlatformToolProvider toolProvider = toolChain.select(targetPlatform);
                if (!toolProvider.producesImportLibrary()) {
                    return null;
                }
                return toolProvider.getImportLibraryName(binaryFile.getAsFile().getAbsolutePath());
            }
        })));
    }

    /**
     * Returns the import library produced by this task. Defaults to the directory containing the runtime file and is not defined when no import library will be produced.
     *
     * @since 4.4
     */
    @Optional @OutputFile
    public RegularFileProperty getImportLibrary() {
        return importLibrary;
    }

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
        Spec spec = new Spec();
        spec.setInstallName(getInstallName());
        spec.setImportLibrary(importLibrary.getAsFile().getOrNull());
        return spec;
    }

    private static class Spec extends DefaultLinkerSpec implements SharedLibraryLinkerSpec {
        private String installName;
        private File importLibrary;

        @Override
        public String getInstallName() {
            return installName;
        }

        void setInstallName(String installName) {
            this.installName = installName;
        }

        @Override
        public File getImportLibrary() {
            return importLibrary;
        }

        void setImportLibrary(File importLibrary) {
            this.importLibrary = importLibrary;
        }
    }
}
