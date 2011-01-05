/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.file.DeleteAction;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DeleteActionImpl implements DeleteAction {
    private static Logger logger = LoggerFactory.getLogger(DeleteActionImpl.class);
    
    private FileResolver fileResolver;

    public DeleteActionImpl(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public boolean delete(Object... deletes) {
        boolean didWork = false;

        for (File file : fileResolver.resolveFiles(deletes)) {
            if (!file.exists()) {
                continue;
            }
            logger.debug("Deleting {}", file);
            didWork = true;
            if (file.isFile()) {
                GFileUtils.deleteQuietly(file);
            } else {
                GFileUtils.deleteDirectory(file);
            }
        }
        return didWork;
    }
}
