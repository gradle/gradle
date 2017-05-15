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

package org.gradle.internal.operations.trace;

import groovy.json.JsonSlurper;
import org.gradle.StartParameter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.util.GFileUtils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * Writes a log of build operation events as they happen to a file.
 * Can be enabled for any build with {@code -Dorg.gradle.internal.operations.trace=«path»}.
 * Also used as the basis for asserting on the event stream in integration tests, via BuildOperationFixture.
 * Imposes no overhead when not enabled.
 *
 * The log file format is one JSON object per line, which will represent a start or finish event.
 * Finish events are indented to make human parsing slightly easier.
 * See {@link SerializedOperationStart} and {@link SerializedOperationFinish} for the exact format.
 *
 * @since 4.0
 */
public class BuildOperationTrace implements Stoppable {

    private static final Logger LOGGER = Logging.getLogger(BuildOperationTrace.class);

    public static final String SYSPROP = "org.gradle.internal.operations.trace";

    private final OutputStream outputStream;

    public BuildOperationTrace(StartParameter startParameter, ListenerManager listenerManager) {
        String filePath = startParameter.getSystemPropertiesArgs().get(SYSPROP);
        if (filePath == null) {
            this.outputStream = null;
            return;
        }

        try {
            File file = new File(filePath);
            GFileUtils.mkdirs(file.getParentFile());
            if (file.isFile()) {
                GFileUtils.forceDelete(file);
            }
            //noinspection ResultOfMethodCallIgnored
            file.createNewFile();

            LOGGER.warn("Logging build operations to: " + file.getAbsolutePath());

            this.outputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        listenerManager.addListener(new SerializingBuildOperationListener(outputStream));
    }

    @Override
    public void stop() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    public static Map<Object, CompleteBuildOperation> read(InputStream input) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));

            JsonSlurper slurper = new JsonSlurper();
            Map<Object, SerializedOperationStart> pending = new HashMap<Object, SerializedOperationStart>();
            Map<Object, CompleteBuildOperation> operations = new HashMap<Object, CompleteBuildOperation>();
            String line = reader.readLine();
            while (line != null) {
                Map<String, ?> map = uncheckedCast(slurper.parseText(line));
                if (map.containsKey("startTime")) {
                    SerializedOperationStart serialized = new SerializedOperationStart(map);
                    pending.put(serialized.id, serialized);
                } else {
                    SerializedOperationFinish finish = new SerializedOperationFinish(map);
                    SerializedOperationStart start = pending.remove(finish.id);
                    assert start != null;

                    Map<String, ?> detailsMap = uncheckedCast(start.details);
                    Map<String, ?> resultMap = uncheckedCast(finish.result);

                    CompleteBuildOperation previous = operations.put(start.id, new CompleteBuildOperation(
                        start.id,
                        start.parentId,
                        start.displayName,
                        start.startTime,
                        finish.endTime,
                        start.detailsType,
                        detailsMap == null ? null : Collections.unmodifiableMap(detailsMap),
                        finish.resultType,
                        resultMap == null ? null : Collections.unmodifiableMap(resultMap),
                        finish.failureMsg
                    ));

                    assert previous == null;
                }
                line = reader.readLine();
            }

            assert pending.isEmpty();

            return operations;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
