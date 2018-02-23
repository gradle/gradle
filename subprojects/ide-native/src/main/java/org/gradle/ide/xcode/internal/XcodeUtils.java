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

package org.gradle.ide.xcode.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Transformer;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Arrays;

public class XcodeUtils {
    private XcodeUtils() {}

    public static String toSpaceSeparatedList(File... files) {
        return toSpaceSeparatedList(Arrays.asList(files));
    }

    public static String toSpaceSeparatedList(Iterable<File> it) {
        return StringUtils.join(CollectionUtils.collect(it, new Transformer<String, File>() {
            @Override
            public String transform(File file) {
                return quote(file.getAbsolutePath());
            }
        }), ' ');
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
