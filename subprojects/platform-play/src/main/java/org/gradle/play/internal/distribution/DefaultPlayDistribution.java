/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.distribution;

import org.gradle.api.distribution.internal.DefaultDistribution;
import org.gradle.api.file.CopySpec;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.distribution.PlayDistribution;

public class DefaultPlayDistribution extends DefaultDistribution implements PlayDistribution {
    final private PlayApplicationBinarySpec binary;

    public DefaultPlayDistribution(String name, CopySpec contents, PlayApplicationBinarySpec binary) {
        super(name, contents);
        this.binary = binary;
    }

    @Override
    public PlayApplicationBinarySpec getBinary() {
        return binary;
    }
}
