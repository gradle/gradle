<html>
<head><title>Randomizer</title></head>
<body>
<%
    double num = Math.random();
    if (num > 0.5) {
%>
<h2>It's your lucky day!</h2><p>(<%= num %>)</p>
<%
} else {
%>
<h2>Sorry...bad day</h2><p>(<%= num %>)</p>
<%
    }
%>
<a href="<%= request.getRequestURI() %>"><h3>Try Again</h3></a>
</body>
</html>
