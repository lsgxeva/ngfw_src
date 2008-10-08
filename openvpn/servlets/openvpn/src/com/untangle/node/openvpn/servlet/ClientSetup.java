/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.openvpn.servlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;


import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;


import org.apache.log4j.Logger;

import com.untangle.uvm.node.IPaddr;
import com.untangle.uvm.node.NodeException;
import com.untangle.uvm.node.ParseException;

import com.untangle.node.openvpn.Constants;
import com.untangle.node.openvpn.VpnNode;


/**
 * Servlet used to upload a client configuration file.
 */
public class ClientSetup extends HttpServlet
{
    private static ServletFileUpload SERVLET_FILE_UPLOAD;

    private final Logger logger = Logger.getLogger( this.getClass());

    protected void doGet(  HttpServletRequest request,  HttpServletResponse response )
    {
        logger.warn( "Retrieve a GET request for client setup." );
    }

    protected void doPost( HttpServletRequest request,  HttpServletResponse response )
        throws ServletException, IOException
    {
        Util util = Util.getInstance();
        VpnNode node = null;

        try {
            node = util.getNode();
        } catch ( NodeException e ) {
            logger.warn( "Unable to retrieve the VPN node.", e );
            response.sendError( HttpServletResponse.SC_UNAUTHORIZED, "Please access this through the admin client." );
            return;
        }
        
        if ( !util.isAdmin( request, node )) {
            logger.debug( "User has invalid authentication key." );
            response.sendError( HttpServletResponse.SC_UNAUTHORIZED, "Please access this through the admin client." );
            return;
        }
        
        if ( !ServletFileUpload.isMultipartContent( request )) {
            logger.debug( "User has invalid post." );
            response.sendError( HttpServletResponse.SC_UNAUTHORIZED, "Please access this through the admin client." );
            return;
        }
        
        ServletFileUpload upload = getServletFileUpload();
        
        List<FileItem> items = null;
        try {
            items = upload.parseRequest( request );
        } catch ( FileUploadException e ) {
            logger.warn( "Unable to parse the request", e );
            response.sendError( HttpServletResponse.SC_UNAUTHORIZED, "Please access this through the admin client." );
            return;
        }
        
        InputStream inputStream = null;

        for ( FileItem item : items ) {
            if ( !Constants.ADMIN_UPLOAD_CLIENT_PARAM.equals( item.getFieldName())) continue;
            
            inputStream = item.getInputStream();
            break;
        }

        if ( inputStream == null ) {
            logger.info( "ClientSetup is missing the client file." );
            response.sendError( HttpServletResponse.SC_UNAUTHORIZED, "Please access this through the admin client." );
            return;
        }
            
        /* Write out the file. */
        File temp = null;
        OutputStream outputStream = null;
        try {
            temp = File.createTempFile( "openvpn-client", ".zip" );

            outputStream = new FileOutputStream( temp );
            
            byte[] data = new byte[1024];
            int len = 0;
            while (( len = inputStream.read( data )) > 0 ) outputStream.write( data, 0, len );
        } catch ( IOException e ) {
            logger.warn( "Unable to validate client file.", e  );
            response.sendError( HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to load client configuration." );
            return;
        } finally {
            try {
                if ( outputStream != null ) outputStream.close();
            } catch ( Exception e ) {
                logger.warn( "Error closing writer", e );
            }

            try {
                if ( inputStream != null ) inputStream.close();
            } catch ( Exception e ) {
                logger.warn( "Error closing input stream", e );
            }
        }
        
        try {
            node.installClientConfig( temp.getPath());
        } catch ( Exception e ) {
            logger.warn( "Unable to install the client configuration", e );
            response.sendError( HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to install the client configuration." );
            return;
        }

        response.getWriter().write( "success" );
    }

    private static synchronized ServletFileUpload getServletFileUpload()
    {
        if ( SERVLET_FILE_UPLOAD == null ) {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            SERVLET_FILE_UPLOAD =  new ServletFileUpload(factory);
        }

        return SERVLET_FILE_UPLOAD;
    }
}
