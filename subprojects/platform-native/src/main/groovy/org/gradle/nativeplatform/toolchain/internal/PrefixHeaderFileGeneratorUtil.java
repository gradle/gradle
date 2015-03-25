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

package org.gradle.nativeplatform.toolchain.internal;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class PrefixHeaderFileGeneratorUtil {
    public static void generatePCHFile(Set<String> headers, File headerFile) {
        if (!headerFile.getParentFile().exists()) {
            headerFile.getParentFile().mkdirs();
        }

        try {
            FileUtils.writeLines(headerFile, CollectionUtils.collect(CollectionUtils.toList(headers), new Transformer<String, String>() {
                @Override
                public String transform(String header) {
                    if (header.startsWith("<")) {
                        return "#include ".concat(header);
                    } else {
                        return "#include \"".concat(header).concat("\"");
                    }
                }
            }));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
