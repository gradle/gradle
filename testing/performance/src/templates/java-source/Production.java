package $

{packageName};

import java.util.List;
import java.util.Arrays;

private final String property;
String param {

$ {extendsAndImplementsClause}

public static $ {productionClassName}

one() {
    return new $ {
        productionClassName
    } ();
}
    <%extraFields.

each { %>
    $ {
        it.modifier
    } $ {
        it.type
    } $ {
        it.name
    } =$ {
        it.type
    }.one();

    public boolean check ($ {
        it.type
    } o){
        // dummy code to add arbitrary compile time
        String p = o.getProperty();
        p = p.toUpperCase(Locale.ROOT);
        List<String> strings = Arrays.asList(p, this.getProperty());
        int len = 0;
        for (String s : strings) {
            len += s.length();
            <%propertyCount.times { %>
                len += o.getProp$ {
                    it
                } ().length();
            <%}%>
        }
        return len > 10;
    }
    <%} %>

public $ {productionClassName}(){
    this.property =null;
    }

public $ {productionClassName}(
times { %>
    private String prop$ {
        it
    } ;

    public String getProp$ {
        it
    } () {
        return prop$ {
            it
        } ;
    }

    public void setProp$ {
        it
    } (String value){
        prop$ {
            it
        } =value;
    }
<%}){
    this.property =param;
    }

public String getProperty() {
    return property;
}
<%propertyCount.

public class $ {
    productionClassName
} %>
    }
