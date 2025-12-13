/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import org.apache.commons.io.FileUtils;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SampleModifier;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 * We use "https://example.com:8123/cache/" as "example remote build cache url",
 * which will cause the sample hang minutes for "Requesting from remote build cache".
 *
 * This modifier clears the init or setting script if they include "https://example.com:8123/cache/"
 */
public class RemoveDeadEndCacheServerModifier implements SampleModifier {
    private static final List<String> MANAGED_FILES = Arrays.asList("settings.gradle", "settings.gradle.kts", "init.gradle", "init.gradle.kts");
    private static final String EXAMPLE_CACHE_SERVER ="https://example.com:8123/cache/";

    @Override
    public Sample modify(Sample sample) {
        File projectDir = sample.getProjectDir();
        for (String fileName : MANAGED_FILES) {
            File file = new File(projectDir, fileName);
            if (file.isFile()) {
                try {
                    String content = FileUtils.readFileToString(file, Charset.defaultCharset());
                    if (content.contains(EXAMPLE_CACHE_SERVER)) {
                        FileUtils.writeStringToFile(file, "\n", Charset.defaultCharset());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return sample;
    }
}
