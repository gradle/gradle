/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.scan;

/**
 * This interface is used to mark that gradle build scan is requested.
 *
 * Usually initiated by done via passing `--scan` from commandline.
 * This interface is intentionally internal and consumend by the build script plugin.
 *
 * @since 3.4
 * */
public interface BuildScanRequest {

    /**
     * Called by Gradle if --scan is present
     * */
    void markRequested();

    /**
     * Called by Gradle if --no-scan is present
     * */
    void markDisabled();

    /***
     *  Called by the build scan plugin to determine if --scan is present
     */
    boolean collectRequested();

    /***
     *  Called by the build scan plugin to determine if --no-scan is present
     */
    boolean collectDisabled();
}
