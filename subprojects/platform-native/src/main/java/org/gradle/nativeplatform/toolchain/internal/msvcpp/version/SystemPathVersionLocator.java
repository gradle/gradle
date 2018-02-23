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

package org.gradle.nativeplatform.toolchain.internal.msvcpp.version;


import com.google.common.collect.Lists;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.os.OperatingSystem;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;

public class SystemPathVersionLocator implements VisualStudioVersionLocator {
    private static final String LEGACY_COMPILER_FILENAME = "cl.exe";

    private static final Logger LOGGER = Logging.getLogger(SystemPathVersionLocator.class);
    private final OperatingSystem os;
    private final VisualStudioMetaDataProvider versionDeterminer;

    public SystemPathVersionLocator(OperatingSystem os, VisualStudioMetaDataProvider versionDeterminer) {
        this.os = os;
        this.versionDeterminer = versionDeterminer;
    }

    @Nonnull
    @Override
    public List<VisualStudioMetadata> getVisualStudioInstalls() {
        List<VisualStudioMetadata> installs = Lists.newArrayList();

        File compilerInPath = os.findInPath(LEGACY_COMPILER_FILENAME);
        if (compilerInPath == null) {
            LOGGER.debug("No visual c++ compiler found in system path.");
        } else {
            VisualStudioMetadata install = versionDeterminer.getVisualStudioMetadataFromCompiler(compilerInPath);
            if (install != null) {
                installs.add(install);
            }
        }

        return installs;
    }

    @Override
    public String getSource() {
        return "system path";
    }
}
