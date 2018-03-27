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

package org.gradle.internal.resolve.result;

import com.google.common.base.Objects;
import org.gradle.api.artifacts.VersionVariants;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DefaultVersionVariants implements VersionVariants {

    private String version;

    private List<Map<String, String>> variants;

    public DefaultVersionVariants(String version) {
        this(version, Collections.<Map<String,String>>emptyList());
    }

    public DefaultVersionVariants(String version, List<Map<String, String>> variants) {
        this.version = version;
        this.variants = variants;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public  List<Map<String, String>>  getVariants() {
        return variants;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultVersionVariants that = (DefaultVersionVariants) o;
        return Objects.equal(version, that.version); // && Objects.equal(variants, that.variants);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(version /*, variants*/);
    }
}
