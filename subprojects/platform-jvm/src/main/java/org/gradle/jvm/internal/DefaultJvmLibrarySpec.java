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

package org.gradle.jvm.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.jvm.JvmByteCode;
import org.gradle.jvm.JvmResources;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.TransformationFileType;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;

import java.util.*;

public class DefaultJvmLibrarySpec extends BaseComponentSpec implements JvmLibrarySpecInternal {
    private final Set<Class<? extends TransformationFileType>> languageOutputs = new HashSet<Class<? extends TransformationFileType>>();
    private final List<PlatformRequirement> targetPlatforms = Lists.newArrayList();
    private final ApiSpec apiSpec = new ApiSpec();

    public DefaultJvmLibrarySpec() {
        this.languageOutputs.add(JvmResources.class);
        this.languageOutputs.add(JvmByteCode.class);
    }

    @Override
    protected String getTypeName() {
        return "JVM library";
    }

    @Override
    public Set<Class<? extends TransformationFileType>> getInputTypes() {
        return languageOutputs;
    }

    @Override
    public List<PlatformRequirement> getTargetPlatforms() {
        return Collections.unmodifiableList(targetPlatforms);
    }

    @Override
    public void targetPlatform(String targetPlatform) {
        this.targetPlatforms.add(DefaultPlatformRequirement.create(targetPlatform));
    }

    void api(Action<ApiSpec> configureAction) {
        configureAction.execute(apiSpec);
    }

    @Override
    public Set<String> getExportedPackages() {
        Iterable<String> transform = Iterables.transform(apiSpec.getExports(), new Function<PackageName, String>() {
            @Override
            public String apply(PackageName packageName) {
                return packageName.toString();
            }
        });
        return ImmutableSet.copyOf(transform);
    }

    @Override
    public Collection<DependencySpec> getApiDependencies() {
        return apiSpec.getDependencies().getDependencies();
    }
}
