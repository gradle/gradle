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
import org.gradle.play.PlayApplicationSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultPlayApplicationSpec extends BaseComponentSpec implements PlayApplicationSpec {
    private final List<String> targetPlatforms = new ArrayList<String>();

    @Override
    protected String getTypeName() {
        return "Play Application";
    }

    @Override
    public List<String> getTargetPlatforms() {
        return Lists.newArrayList(targetPlatforms);
    }

    @Override
    public void targetPlatform(String... targetPlatforms) {
        Collections.addAll(this.targetPlatforms, targetPlatforms);
    }
}
