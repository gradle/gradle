import com.sun.tools.doclets.Taglet;
import com.sun.javadoc.*;

import java.util.Map;

public class CustomTaglet implements Taglet {
    public boolean inField() {
        return false;
    }

    public boolean inConstructor() {
        return false;
    }

    public boolean inMethod() {
        return false;
    }

    public boolean inOverview() {
        return false;
    }

    public boolean inPackage() {
        return false;
    }

    public boolean inType() {
        return true;
    }

    public boolean isInlineTag() {
        return false;
    }

    public String getName() {
        return "customtaglet";
    }

    public String toString(Tag tag) {
        return "<DT><B>Custom Taglet:</B></DT>\n<DD>" + tag.text() + "</DD>\n";
    }

    public String toString(Tag[] tags) {
        return toString(tags[0]);
    }

    public static void register(Map tagletMap) {
        CustomTaglet taglet = new CustomTaglet();
        tagletMap.put(taglet.getName(), taglet);
    }
}
