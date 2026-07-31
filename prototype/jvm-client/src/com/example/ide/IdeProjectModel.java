/*
 * Copyright 2026 the original author or authors.
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
package com.example.ide;

import java.util.List;

/**
 * The client-side Tooling API view of the plugin's model. Its FQCN is the model identifier the
 * plugin's ToolingModelBuilder answers to, and its getters mirror the protobuf IdeProjectModel, so
 * the Tooling API adapts the daemon's protobuf message onto this interface. (The Tooling API only
 * fetches interfaces, never the concrete protobuf class.)
 */
public interface IdeProjectModel {
    String getProjectPath();

    String getProjectName();

    String getBuildDir();

    List<String> getPluginIdsList();
}
