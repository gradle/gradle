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
import com.google.gson.stream.JsonReader;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.io.NullOutputStream;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class CommandLineToolVersionLocator extends AbstractVisualStudioVersionLocator implements VisualStudioVersionLocator {
    private static final Logger LOGGER = Logging.getLogger(CommandLineToolVersionLocator.class);

    private final ExecActionFactory execActionFactory;
    private final VisualCppMetadataProvider visualCppMetadataProvider;
    private final VswhereVersionLocator vswhereLocator;

    private static final String INSTALLATION_PATH_KEY = "installationPath";
    private static final String INSTALLATION_VERSION_KEY = "installationVersion";

    public CommandLineToolVersionLocator(ExecActionFactory execActionFactory, VisualCppMetadataProvider visualCppMetadataProvider, VswhereVersionLocator vswhereLocator) {
        this.execActionFactory = execActionFactory;
        this.visualCppMetadataProvider = visualCppMetadataProvider;
        this.vswhereLocator = vswhereLocator;
    }

    @Override
    protected List<VisualStudioMetadata> locateInstalls() {
        List<VisualStudioMetadata> installs = Lists.newArrayList();

        File vswhereBinary = vswhereLocator.getVswhereInstall();
        if (vswhereBinary != null) {
            List<String> args = Lists.newArrayList("-all", "-legacy", "-format", "json");
            String json = getVswhereOutput(vswhereBinary, args);
            installs.addAll(parseJson(json));
        }

        return installs;
    }

    @Override
    public String getSource() {
        return "command line tool";
    }

    private String getVswhereOutput(File vswhereBinary, List<String> args) {
        ExecAction exec = execActionFactory.newExecAction();
        exec.args(args);
        exec.executable(vswhereBinary.getAbsolutePath());
        exec.setWorkingDir(vswhereBinary.getParentFile());

        StreamByteBuffer buffer = new StreamByteBuffer();
        exec.setStandardOutput(buffer.getOutputStream());
        exec.setErrorOutput(NullOutputStream.INSTANCE);
        exec.setIgnoreExitValue(true);
        ExecResult result = exec.execute();

        int exitValue = result.getExitValue();
        if (exitValue == 0) {
            return buffer.readAsString("UTF-8");
        } else {
            LOGGER.debug("vswhere.exe returned a non-zero exit value ({}) - ignoring", result.getExitValue());
            return null;
        }
    }

    private List<VisualStudioMetadata> parseJson(String json) {
        List<VisualStudioMetadata> installs = Lists.newArrayList();
        JsonReader reader = new JsonReader(new StringReader(json));
        try {
            try {
                reader.beginArray();
                while (reader.hasNext()) {
                    installs.add(readInstall(reader));
                }
                reader.endArray();
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        return installs;
    }

    private VisualStudioMetadata readInstall(JsonReader reader) throws IOException {
        String visualStudioInstallPath = null;
        String visualStudioVersion = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals(INSTALLATION_PATH_KEY)) {
                visualStudioInstallPath = reader.nextString();
            } else if (key.equals(INSTALLATION_VERSION_KEY)) {
                visualStudioVersion = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        File visualStudioInstallDir = new File(visualStudioInstallPath);
        VisualCppMetadata visualCppMetadata = findVisualCppMetadata(visualStudioInstallDir, visualStudioVersion);
        return new VisualStudioMetadataBuilder()
            .installDir(visualStudioInstallDir)
            .visualCppDir(visualCppMetadata.getVisualCppDir())
            .version(VersionNumber.parse(visualStudioVersion))
            .visualCppVersion(visualCppMetadata.getVersion())
            .build();
    }

    private VisualCppMetadata findVisualCppMetadata(File installDir, String version) {
        if (VersionNumber.parse(version).getMajor() >= 15) {
            return visualCppMetadataProvider.getVisualCppFromMetadataFile(installDir);
        } else {
            return visualCppMetadataProvider.getVisualCppFromRegistry(version);
        }
    }
}
