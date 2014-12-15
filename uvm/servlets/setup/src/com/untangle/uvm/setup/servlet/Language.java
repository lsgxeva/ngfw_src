/**
 * $Id$
 */
package com.untangle.uvm.setup.servlet;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jabsorb.JSONSerializer;
import org.jabsorb.serializer.MarshallException;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.UvmContext;

import com.untangle.uvm.LanguageManager;

/**
 * A servlet which will display the start page
 */
@SuppressWarnings("serial")
public class Language extends HttpServlet
{
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException 
    {
        UvmContext context = UvmContextFactory.context();
        request.setAttribute( "skinSettings", context.skinManager().getSettings());
        request.setAttribute( "timezone", context.adminManager().getTimeZone());

        /* Retrieve the list of languages and serialize it for the setup wizard. */

        JSONSerializer js = new JSONSerializer();

        try {
            js.registerDefaultSerializers();
        } catch ( Exception e ) {
            throw new ServletException( "Unable to load the default serializer", e );
        }

        LanguageManager rlm = context.languageManager();
        
        try {
            request.setAttribute( "languageList", js.toJSON( rlm.getLanguagesList()));
            request.setAttribute( "language", rlm.getLanguageSettings().getLanguage());
        } catch ( MarshallException e ) {
            throw new ServletException( "Unable to serializer JSON", e );
        }
            
        String url="/WEB-INF/jsp/language.jsp";
        ServletContext sc = getServletContext();
        RequestDispatcher rd = sc.getRequestDispatcher(url);
        request.setAttribute( "buildStamp", getServletConfig().getInitParameter("buildStamp") );
        rd.forward(request, response);
    }
}
