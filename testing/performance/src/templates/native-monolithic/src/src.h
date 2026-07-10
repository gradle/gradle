
#ifndef PROJECT_HEADER_${sourceIdx}_H
#define PROJECT_HEADER_${sourceIdx}_H

<% sourceIdx.times { %>
#include "src${it}_h.h"
<% } %>


<% functionCount.times { %>
int C_function_${(it+1)+offset} (); 
int CPP_function_${(it+1)+offset} (); 
<% } %>
#endif // PROJECT_HEADER_${sourceIdx}_H