/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.versions;

import groovy.json.JsonSlurper;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Factory;
import org.gradle.util.GradleVersion;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import static org.gradle.integtests.fixtures.versions.ReleasedGradleVersion.Type.*;
import static org.gradle.util.CollectionUtils.collect;

// This can't be implemented in Groovy because it can't handle the generic interface implementation
public class VersionWebServiceJsonParser implements Factory<List<ReleasedGradleVersion>> {

    private final Factory<Reader> jsonSource;

    public VersionWebServiceJsonParser(Factory<Reader> jsonSource) {
        this.jsonSource = jsonSource;
    }

    public List<ReleasedGradleVersion> create() {
        List<Map<String, ?>> list = readJson();
        return collect(list, new Transformer<ReleasedGradleVersion, Map<String, ?>>() {
            public ReleasedGradleVersion transform(Map<String, ?> jsonEntry) {
                ReleasedGradleVersion.Type type;
                boolean current;

                boolean jsonNightly = (Boolean) jsonEntry.get("nightly");
                String jsonRcFor = (String) jsonEntry.get("rcFor");
                boolean jsonActiveRc = (Boolean) jsonEntry.get("activeRc");
                boolean jsonCurrent = (Boolean) jsonEntry.get("current");
                String jsonVersion = (String) jsonEntry.get("version");

                if (jsonNightly) {
                    type = NIGHTLY;
                    current = true;
                } else if (jsonRcFor != null && jsonRcFor.length() > 0) {
                    type = RELEASE_CANDIDATE;
                    current = jsonActiveRc;
                } else {
                    type = FINAL;
                    current = jsonCurrent;
                }

                return new ReleasedGradleVersion(GradleVersion.version(jsonVersion), type, current);
            }
        });
    }

    private List<Map<String, ?>> readJson() {
        Reader reader = jsonSource.create();
        try {
            //noinspection unchecked
            return (List<Map<String, ?>>) new JsonSlurper().parse(reader);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                //noinspection ThrowFromFinallyBlock
                throw new UncheckedIOException(e);
            }
        }
    }
}
