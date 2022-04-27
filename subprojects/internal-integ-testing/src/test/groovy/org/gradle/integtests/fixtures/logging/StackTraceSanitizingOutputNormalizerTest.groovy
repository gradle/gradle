/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.logging

import org.gradle.exemplar.executor.ExecutionMetadata
import spock.lang.Specification

class StackTraceSanitizingOutputNormalizerTest extends Specification {
    def normalizer = new StackTraceSanitizingOutputNormalizer()

    def 'remove harmless stacktraces in android build output'() {
        given:
        File dir = File.createTempFile("build-android-app", "")
        def originalOutput = '''
Unable to detect AGP versions for included builds. All projects in the build should use the same AGP version. Class name for the included build object: org.gradle.composite.internal.DefaultIncludedBuild$IncludedBuildImpl_Decorated.\t
Unable to detect AGP versions for included builds. All projects in the build should use the same AGP version. Class name for the included build object: org.gradle.composite.internal.DefaultIncludedBuild$IncludedBuildImpl_Decorated.\t
File /home/tcagent1/.android/repositories.cfg could not be loaded.\t
IO exception while downloading manifest:\t
java.io.FileNotFoundException: https://dl.google.com/android/repository/sys-img/android-desktop/sys-img2-1.xml\t
\tat sun.net.www.protocol.http.HttpURLConnection.getInputStream0(HttpURLConnection.java:1896)\t
\tat sun.net.www.protocol.http.HttpURLConnection.getInputStream(HttpURLConnection.java:1498)\t
\tat sun.net.www.protocol.https.HttpsURLConnectionImpl.getInputStream(HttpsURLConnectionImpl.java:268)\t
\tat com.android.sdklib.repository.legacy.remote.internal.DownloadCache.openUrl(DownloadCache.java:248)\t
\tat com.android.sdklib.repository.legacy.remote.internal.DownloadCache.downloadAndCache(DownloadCache.java:624)\t
\tat com.android.sdklib.repository.legacy.remote.internal.DownloadCache.openCachedUrl(DownloadCache.java:547)\t
\tat com.android.sdklib.repository.legacy.LegacyDownloader.downloadAndStream(LegacyDownloader.java:61)\t
\tat com.android.repository.impl.downloader.LocalFileAwareDownloader.downloadAndStream(LocalFileAwareDownloader.java:51)\t
\tat com.android.repository.impl.manager.RemoteRepoLoaderImpl.lambda$fetchPackages$0(RemoteRepoLoaderImpl.java:139)\t
\tat java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511)\t
\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\t
\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)\t
\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)\t
\tat java.lang.Thread.run(Thread.java:748)\t
Checking the license for package Android SDK Platform-Tools in /opt/android/sdk/licenses\t
License for package Android SDK Platform-Tools accepted.\t
Preparing "Install Android SDK Platform-Tools (revision: 33.0.1)".\t
"Install Android SDK Platform-Tools (revision: 33.0.1)" ready.
'''

        expect:
        normalizer.normalize(originalOutput, new ExecutionMetadata(dir, [:])) == '''
Unable to detect AGP versions for included builds. All projects in the build should use the same AGP version. Class name for the included build object: org.gradle.composite.internal.DefaultIncludedBuild$IncludedBuildImpl_Decorated.\t
Unable to detect AGP versions for included builds. All projects in the build should use the same AGP version. Class name for the included build object: org.gradle.composite.internal.DefaultIncludedBuild$IncludedBuildImpl_Decorated.\t
File /home/tcagent1/.android/repositories.cfg could not be loaded.\t
IO exception while downloading manifest:\t
Checking the license for package Android SDK Platform-Tools in /opt/android/sdk/licenses\t
License for package Android SDK Platform-Tools accepted.\t
Preparing "Install Android SDK Platform-Tools (revision: 33.0.1)".\t
"Install Android SDK Platform-Tools (revision: 33.0.1)" ready.
'''
    }
}
