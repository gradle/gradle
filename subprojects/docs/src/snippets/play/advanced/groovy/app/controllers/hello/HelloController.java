package controllers.hello;

import play.*;
import play.mvc.*;
import views.html.*;

import org.apache.commons.lang.StringUtils;

public class HelloController extends Controller {

    public Result index(String name) {
        return ok(String.format("Hello %s!", StringUtils.capitalize(name)));
    }
}
