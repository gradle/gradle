/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.provider.input;

import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;

import java.io.InputStream;

/**
 * Defines what information is needed on the provider side regarding the build operation.
 * <p>
 * by Szczepan Faber, created at: 12/20/11
 */
public interface ProviderOperationParameters extends BuildOperationParametersVersion1 {

    boolean getVerboseLogging();

    InputStream getStandardInput();
}
