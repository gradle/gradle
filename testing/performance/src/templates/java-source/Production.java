package ${packageName};

import java.util.List;
import java.util.Arrays;

public class ${productionClassName} ${extendsAndImplementsClause} {

    public static ${productionClassName} one() { return new ${productionClassName}(); }

    private final String property;
    <% extraFields.each { %>
    ${it.modifier} ${it.type} ${it.name} = ${it.type}.one();

    public boolean check(${it.type} o) {
        // dummy code to add arbitrary compile time
        String p = o.getProperty();
        p = p.toUpperCase(Locale.ROOT);
        List<String> strings = Arrays.asList(p, this.getProperty());
        int len = 0;
        for (String s: strings) {
            len += s.length();
            <% propertyCount.times { %>
            len += o.getProp${it}().length();
            <%}%>
        }
        return len>10;
    }
    <% } %>

    public ${productionClassName}(){
        this.property = null;
    }

    public ${productionClassName}(String param) {
        this.property = param;
    }

    public String getProperty() {
        return property;
    }
<% propertyCount.times { %>
    private String prop${it};

    public String getProp${it}() {
        return prop${it};
    }

    public void setProp${it}(String value) {
        prop${it} = value;
    }
<% } %>
}
