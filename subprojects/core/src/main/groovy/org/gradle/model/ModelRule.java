/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model;

import org.gradle.api.Incubating;

/**
 * An model object mutation rule, provided to {@link ModelRules#rule(ModelRule)}.
 *
 * <p>Subclasses should provide one and only one public method, with any name and any signature.
 * This method will be inspected for bindings.
 *
 * <p>The first parameter of this method is considered the target or 'output' of the rule. The rule is free to modify the
 * object as appropriate.
 *
 * <p>The subsequent parameters of this method are considered the parameters or 'inputs' of the rule. The rule should not
 * modify these objects. Rules are ordered so that the input to a rule is completely configured before the rule is invoked.
 *
 * <p>The ordering of rules with the same target object is currently undefined and rules are executed in some arbitrary but
 * fixed order. There is some basic support for controlling the ordering, where all rules that extend {@link ModelFinalizer}
 * are executed after all rules that extend {@link ModelRule}.
 */
@Incubating
public abstract class ModelRule {

}
