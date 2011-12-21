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

package org.gradle.tooling.internal.protocol;

/**
 * I needed this interface so that it is possible to develop new features incrementally.
 * In general I'd like to avoid growing VersionX interfaces
 * because we have an excellent test suite that tells the story of what has changed and when
 * <p>
 * A marker interface to document the problem consistently.
 * Might live only until we gradually remove old VersionX types.
 * <p>
 * If you make changes to inheritor of this interfaces make sure you run all compatibility tests.
 *
 * @author: Szczepan Faber, created at: 8/5/11
 */
public interface InternalProtocolInterface {
}
