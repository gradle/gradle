#ifndef PROJECT_HEADER_${functionName}_H
#define PROJECT_HEADER_${functionName}_H
<% functionCount.times { %>
int CPP_${functionName}_${it + 1} ();
<% } %>
#endif // PROJECT_HEADER_${functionName}_H
