/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.file;

import com.google.common.collect.ImmutableList;
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.BuildAdapter;
import org.gradle.api.initialization.Settings;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.excludes.FileSystemDefaultExcludesListener;

import java.util.List;

public class DefaultFileSystemDefaultExcludesProvider implements FileSystemDefaultExcludesProvider {

    private ImmutableList<String> currentDefaultExcludes = ImmutableList.copyOf(DirectoryScanner.getDefaultExcludes());

    private final AnonymousListenerBroadcast<FileSystemDefaultExcludesListener> broadcast;

    public DefaultFileSystemDefaultExcludesProvider(ListenerManager listenerManager) {
        broadcast = listenerManager.createAnonymousBroadcaster(FileSystemDefaultExcludesListener.class);

        listenerManager.addListener(new RootBuildLifecycleListener() {
            @Override
            public void afterStart() {
                DirectoryScanner.resetDefaultExcludes();
                currentDefaultExcludes = ImmutableList.copyOf(DirectoryScanner.getDefaultExcludes());
                broadcast.getSource().onDefaultExcludesChanged(currentDefaultExcludes);
            }

            @Override
            public void beforeComplete() {
            }
        });

        listenerManager.addListener(new BuildAdapter() {

            @Override
            public void settingsEvaluated(Settings settings) {
                currentDefaultExcludes = ImmutableList.copyOf(DirectoryScanner.getDefaultExcludes());
                broadcast.getSource().onDefaultExcludesChanged(currentDefaultExcludes);
            }
        });
    }

    @Override
    public List<String> getCurrentDefaultExcludes() {
        return currentDefaultExcludes;
    }

    @Override
    public void updateCurrentDefaultExcludes(List<String> excludes) {
        DirectoryScanner.resetDefaultExcludes();
        for (String exclude : excludes) {
            DirectoryScanner.addDefaultExclude(exclude);
        }

        currentDefaultExcludes = ImmutableList.copyOf(excludes);
        broadcast.getSource().onDefaultExcludesChanged(currentDefaultExcludes);
    }
}
