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

package controllers.date;

import play.*;
import play.mvc.*;
import views.html.*;

import org.apache.commons.lang.StringUtils;

import java.util.Date;

public class DateController extends Controller {

    public static Result index() {
        return ok(String.format("The current date is %s!", new Date()));
    }
}
