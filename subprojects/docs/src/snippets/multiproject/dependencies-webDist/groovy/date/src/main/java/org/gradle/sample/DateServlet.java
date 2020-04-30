package org.gradle.sample;

import java.io.*;
import java.util.*;
import javax.servlet.http.*;
import javax.servlet.*;

public class DateServlet extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.getWriter().println("Date: " + new Date());
    }
}