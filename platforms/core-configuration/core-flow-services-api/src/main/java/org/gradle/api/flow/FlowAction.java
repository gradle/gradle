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

package org.gradle.api.flow;

import org.gradle.api.Incubating;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.process.ExecOperations;

/**
 * A dataflow action.
 *
 * <p>
 * A parameterized and isolated piece of work that becomes eligible for execution within a
 * {@link FlowScope dataflow scope} as soon as all of its input {@link FlowParameters parameters}
 * become available.
 * </p>
 * <p>
 * Implementations can benefit from constructor injection of services
 * using the {@link javax.inject.Inject @Inject} annotation.
 * Currently, only a small subset of services is supported:
 * </p>
 * <ul>
 *     <li>{@link ArchiveOperations} provides means to operate on archives such as ZIP or TAR files.</li>
 *     <li>{@link ExecOperations} provides means to execute external processes.</li>
 *     <li>{@link FileSystemOperations} provides means to operate on the file system.</li>
 * </ul>
 * <pre class='autoTested'>
 * /**
 *  * Plays a given {{@literal @}link Parameters#getMediaFile() media file} using {{@literal @}code ffplay}.
 *  *{@literal /}
 * class FFPlay implements FlowAction&lt;FFPlay.Parameters&gt; {
 *
 *     interface Parameters extends FlowParameters {
 *         {@literal @}Input
 *         Property&lt;File&gt; getMediaFile();
 *     }
 *
 *     private final ExecOperations execOperations;
 *
 *     {@literal @}Inject
 *     FFPlay(ExecOperations execOperations) {
 *         this.execOperations = execOperations;
 *     }
 *
 *     {@literal @}Override
 *     public void execute(Parameters parameters) throws Exception {
 *         execOperations.exec(spec -&gt; {
 *             spec.commandLine(
 *                 "ffplay", "-nodisp", "-autoexit", "-hide_banner", "-loglevel", "quiet",
 *                 parameters.getMediaFile().get().getAbsolutePath()
 *             );
 *             spec.setIgnoreExitValue(true);
 *         });
 *     }
 * }
 * </pre>
 *
 * @param <P> Parameter type for the dataflow action. Should be {@link FlowParameters.None} if the action does not have parameters.
 * @see FlowScope
 * @see FlowProviders
 * @since 8.1
 */
@Incubating
public interface FlowAction<P extends FlowParameters> {

    void execute(P parameters) throws Exception;
}
