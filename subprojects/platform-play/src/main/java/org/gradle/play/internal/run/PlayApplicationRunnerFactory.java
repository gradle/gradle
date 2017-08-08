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

package org.gradle.play.internal.run;

import org.gradle.play.internal.platform.PlayMajorVersion;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.process.internal.worker.WorkerProcessFactory;

public class PlayApplicationRunnerFactory {
    public static PlayApplicationRunner create(PlayPlatform playPlatform, WorkerProcessFactory workerFactory) {
        return new PlayApplicationRunner(workerFactory, createPlayRunAdapter(playPlatform));
    }

    public static VersionedPlayRunAdapter createPlayRunAdapter(PlayPlatform playPlatform) {
        switch (PlayMajorVersion.forPlatform(playPlatform)) {
            case PLAY_2_2_X:
                return new PlayRunAdapterV22X();
            case PLAY_2_4_X:
                return new PlayRunAdapterV24X();
            case PLAY_2_5_X:
                return new PlayRunAdapterV25X();
            case PLAY_2_6_X:
                return new PlayRunAdapterV26X();
            case PLAY_2_3_X:
            default:
                return new PlayRunAdapterV23X();
        }
    }
}
