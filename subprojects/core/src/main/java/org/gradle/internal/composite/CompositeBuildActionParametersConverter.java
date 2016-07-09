/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.composite;

import com.google.common.collect.Lists;
import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CompositeBuildActionParametersConverter {
    public static CompositeParameters forStartParameter(StartParameter startParameter) {
        List<GradleParticipantBuild> participants = determineCompositeParticipants(startParameter);
        return new CompositeParameters(participants);
    }

    private static List<GradleParticipantBuild> determineCompositeParticipants(final StartParameter startParameter) {
        File projectDir = GUtil.elvis(startParameter.getProjectDir(), startParameter.getCurrentDir());
        List<File> participantPaths = Lists.newArrayList();
        try {
            participantPaths.add(projectDir.getCanonicalFile());
            for (File file : startParameter.getParticipantBuilds()) {
                participantPaths.add(file.getCanonicalFile());
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        return CollectionUtils.collect(participantPaths, new Transformer<GradleParticipantBuild, File>() {
            @Override
            public GradleParticipantBuild transform(File participantPath) {
                return new DefaultGradleParticipantBuild(participantPath, null, null, null);
            }
        });
    }

}
