#pragma once

<% functionCount.times { %>
int CPP_${functionName}_${it + 1} ();
<% } %>
