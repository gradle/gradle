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

package org.gradle.play.internal;

import com.google.common.collect.Lists;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;

import java.util.Collections;
import java.util.List;

public class DefaultPlayApplicationSpec extends BaseComponentSpec implements PlayApplicationSpecInternal {
    private final List<PlatformRequirement> targetPlatforms = Lists.newArrayList();

    protected String getTypeName() {
        return "Play Application";
    }

    public List<PlatformRequirement> getTargetPlatforms() {
        return Collections.unmodifiableList(targetPlatforms);
    }

    public void targetPlatform(String targetPlatform) {
        this.targetPlatforms.add(DefaultPlatformRequirement.create(targetPlatform));
    }
}
