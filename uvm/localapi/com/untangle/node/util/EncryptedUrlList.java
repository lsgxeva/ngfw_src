/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id: SpywareHttpHandler.java 8668 2007-01-29 19:17:09Z amread $
 */

package com.untangle.node.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import org.apache.log4j.Logger;
import sun.misc.BASE64Decoder;

public class EncryptedUrlList extends UrlList
{
    private static final byte[] DB_SALT = "oU3q.72p".getBytes();

    private static final Pattern TUPLE_PATTERN = Pattern.compile("([+-])([0-9A-F]+)\t([A-Za-z0-9+/=]+)?");

    private final Logger logger = Logger.getLogger(getClass());

    public EncryptedUrlList(File dbHome, URL databaseUrl, String dbName)
        throws DatabaseException, IOException
    {
        super(dbHome, databaseUrl, dbName);
    }

    // UrlList methods --------------------------------------------------------

    protected void updateDatabase(Database db, BufferedReader br)
        throws IOException
    {
        String line;
        while (null != (line = br.readLine())) {
            Matcher matcher = TUPLE_PATTERN.matcher(line);
            if (matcher.find()) {
                boolean add = matcher.group(1).equals("+");
                byte[] host = new BigInteger(matcher.group(2), 16).toByteArray();

                try {
                    if (add) {
                        byte[] regexp = base64Decode(matcher.group(3));
                        db.put(null, new DatabaseEntry(host),
                               new DatabaseEntry(regexp));
                    } else {
                        db.delete(null, new DatabaseEntry(host));
                    }
                } catch (DatabaseException exn) {
                    logger.warn("could not add database entry", exn);
                }
            }
        }
    }

    protected byte[] getKey(byte[] host)
    {
        byte[] in = new byte[DB_SALT.length + host.length];
        System.arraycopy(DB_SALT, 0, in, 0, DB_SALT.length);

        System.arraycopy(host, 0, in, DB_SALT.length, host.length);

        // XXX Switch to Fast MD5 http://www.twmacinta.com/myjava/fast_md5.php
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException exn) {
            logger.warn("Could not get MD5 algorithm", exn);
            return null;
        }

        return md.digest(in);
    }

    protected List<String> getValues(byte[] host, byte[] data)
    {
        byte[] buf = new byte[8 + DB_SALT.length + host.length];
        System.arraycopy(DB_SALT, 0, buf, 0, DB_SALT.length);
        System.arraycopy(data, 0, buf, DB_SALT.length, 8);
        System.arraycopy(host, 0, buf, 8 + DB_SALT.length, host.length);

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException exn) {
            logger.warn("Could not get MD5 algorithm", exn);
            return Collections.emptyList();
        }
        buf = md.digest(buf);

        Cipher arcfour;
        try {
            arcfour = Cipher.getInstance("ARCFOUR");
            Key key = new SecretKeySpec(buf, "ARCFOUR");
            arcfour.init(Cipher.DECRYPT_MODE, key);
        } catch (GeneralSecurityException exn) {
            logger.warn("could not get ARCFOUR algorithm", exn);
            return Collections.emptyList();
        }

        try {
            buf = arcfour.doFinal(data, 8, data.length - 8);
        } catch (GeneralSecurityException exn) {
            logger.warn("could not decrypt regexp", exn);
            return Collections.emptyList();
        }

        return split(buf);
    }

    protected boolean matches(String str, String pat)
    {
        try {
            return str.matches(pat);
        } catch (PatternSyntaxException exn) {
            logger.warn("bad pattern", exn);
            return false;
        }
    }

    // private methods --------------------------------------------------------

    private byte[] base64Decode(String s) {
        try {
            return new BASE64Decoder().decodeBuffer(s);
        } catch (IOException exn) {
            logger.warn("could not decode", exn);
            return new byte[0];
        }
    }
}
