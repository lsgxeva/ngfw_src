/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.engine;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.RealmBase;
import org.apache.log4j.Logger;


class MvvmRealm extends RealmBase
{
    private static final Logger logger = Logger.getLogger(MvvmRealm.class);

    private static final String userQuery
        = "SELECT password FROM mvvm_user WHERE login = ?";

    private static final String roleQuery
        = "SELECT role_name FROM mvvm_role WHERE login = ?";


    public Principal authenticate(String username, String credentials)
    {
        Connection c = null;
        try {
            // XXX use pool
            c = DriverManager.getConnection("jdbc:postgresql://localhost/mvvm",
                                            "metavize", "foo");

            logger.debug("doing query: " + userQuery);
            PreparedStatement ps = c.prepareStatement(userQuery);
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                logger.warn("no such user: " + username);
                return null;
            }

            byte[] hashedPasswd  = rs.getBytes("password");
            if (!check(credentials, hashedPasswd)) {
                logger.warn("bad password for user: " + username);
                return null;
            }
        } catch (SQLException exn) {
            logger.warn("could not query domains", exn);
        } finally {
            try {
                if (null != c) {
                    c.close();
                }
            } catch (SQLException exn) {
                logger.warn(exn);
            }
        }

        List roles = new LinkedList();
        roles.add("user");

        return new GenericPrincipal(this, username, credentials, roles);
    }

    public Principal authenticate(String username, byte[] credentials)
    {
        return authenticate(username, credentials.toString());
    }


    // protected methods ------------------------------------------------------

    protected String getPassword(String username) { return null; }
    protected Principal getPrincipal(String username) { return null; }
    protected String getName() { return "MvvmRealm"; }

    // private methods --------------------------------------------------------

    // XXX im too lazy , ill just paste from PasswdUtil
    private static final String PASSWORD_HASH_ALGORITHM = "MD5";
    private static final int SALT_LENGTH = 8;

    // XXX im too lazy , ill just paste from PasswdUtil
    private static boolean check(String passwd, byte[] hashedPasswd)
    {
        if (hashedPasswd.length - SALT_LENGTH < 1)
            throw new IllegalArgumentException("hashed passwd is too short");

        byte[] salt = new byte[SALT_LENGTH];
        byte[] rawPW = new byte[hashedPasswd.length - SALT_LENGTH];
        System.arraycopy(hashedPasswd, 0, rawPW, 0, rawPW.length);
        System.arraycopy(hashedPasswd, rawPW.length, salt, 0, SALT_LENGTH);
        MessageDigest d = null;
        try {
            d = MessageDigest.getInstance(PASSWORD_HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException x) {
            throw new Error("Algorithm " + PASSWORD_HASH_ALGORITHM
                            + " not available in Java VM");
        }
        d.reset();
        d.update(passwd.getBytes());
        d.update(salt);
        byte[] testRawPW = d.digest();
        if (rawPW.length != testRawPW.length)
            throw new IllegalArgumentException
                ("hashed password has incorrect length");
        for (int i = 0; i < testRawPW.length; i++)
            if (testRawPW[i] != rawPW[i])
                return false;
        return true;
    }
}
