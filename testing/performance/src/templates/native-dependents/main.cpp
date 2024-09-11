
#include <iostream>
<%
includes.each { include ->
    out.println "#include \"$include\""
}
%>

int main(int argc, char**argv) {
    return 0;
}
