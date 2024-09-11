#ifndef HEADER_${generatedId}_H
#define HEADER_${generatedId}_H

<%
includes.each { include ->
    out.println "#include \"$include\""
}
%>

int function_${generatedId}();

#endif // HEADER_${generatedId}_H