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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import org.apache.log4j.Logger;

/**
 * Holds a list of hostname, data keypairs backed by
 * BerkeleyDB. Intended as a data store for lists provided by servers
 * implementing the Phishing Protection Server Spec
 * (http://wiki.mozilla.org/Phishing_Protection:_Server_Spec).
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public abstract class UrlList
{
    private static final byte[] VERSION_KEY = "__version".getBytes();
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\[[^ ]+ ([0-9.]+)( update)?\\]");

    private static final Set<String> DB_LOCKS = new HashSet<String>();

    private final URL baseUrl;
    private final String dbName;
    private final Database db;
    private final String dbLock;

    private final Logger logger = Logger.getLogger(getClass());

    // constructors -----------------------------------------------------------

    public UrlList(File dbHome, URL baseUrl, String dbName)
        throws DatabaseException
    {
        this.baseUrl = baseUrl;
        this.dbName = dbName;

        dbLock = new File(dbHome, dbName).getAbsolutePath().intern();

        EnvironmentConfig envCfg = new EnvironmentConfig();
        envCfg.setAllowCreate(true);
        Environment dbEnv = new Environment(dbHome, envCfg);

        // Open the database. Create it if it does not already exist.
        DatabaseConfig dbCfg = new DatabaseConfig();
        dbCfg.setAllowCreate(true);
        db = dbEnv.openDatabase(null, dbName, dbCfg);
    }

    // public methods ---------------------------------------------------------

    public void update(boolean async) {
        if (async) {
            Thread t = new Thread(new Runnable() {
                    public void run()
                    {
                        update();
                    }
                }, "update-" + dbLock);
            t.setDaemon(true);
            t.start();
        } else {
            update();
        }
    }

    public void close()
        throws DatabaseException
    {
        db.close();
        db.getEnvironment().close();
    }

    // this method for pre-normalized parts
    public boolean contains(String proto, String host, String uri)
    {
        String url = proto + "://" + host + uri;

        for (String p : getPatterns(host)) {
            if (matches(url, p)) {
                return true;
            }
        }

        return false;
    }

    // protected methods ------------------------------------------------------

    protected abstract void updateDatabase(Database db, BufferedReader br)
        throws IOException;

    protected abstract byte[] getKey(byte[] host);
    protected abstract List<String> getValues(byte[] host, byte[] data);
    protected abstract boolean matches(String str, String pattern);

    protected List<String> split(byte[] buf)
    {
        List<String> l = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buf.length; i++) {
            char c = (char)buf[i];
            if ('\t' == c) {
                l.add(sb.toString());
                sb.delete(0, sb.length());
            } else {
                sb.append(c);
            }
        }
        l.add(sb.toString());

        return l;
    }

    protected byte[] del(byte[] data, byte[] prefix)
    {
        String dStr = new String(data);
        String pStr = new String(prefix);
        int i = dStr.indexOf(pStr);
        if (0 <= i) {
            int j;
            if (dStr.length() > i + pStr.length()
                && '\t' == dStr.charAt(i + pStr.length())) {
                j = i + pStr.length() + 1;
            } else {
                j = i + pStr.length();
            }
            StringBuilder sb = new StringBuilder(dStr.length());
            sb.append(dStr.subSequence(0, i));
            sb.append(dStr.subSequence(j, dStr.length()));
            return sb.toString().getBytes();
        } else {
            return data;
        }
    }

    protected byte[] add(byte[] data, byte[] prefix)
    {
        String dStr = new String(data);
        String pStr = new String(prefix);
        int i = dStr.indexOf(pStr);
        return (0 > i) ? (dStr + "\t" + pStr).getBytes() : data;
    }

    // private methods --------------------------------------------------------

    private List<String> getPatterns(String hostStr)
    {
        byte[] host = hostStr.getBytes();

        byte[] hash = getKey(host);

        byte[] hippie = new byte[hash.length + 1];
        System.arraycopy(hash, 0, hippie, 1, hash.length);

        if (null == hash) {
            return Collections.emptyList();
        }
        DatabaseEntry k = new DatabaseEntry(hash);
        DatabaseEntry v = new DatabaseEntry();

        OperationStatus status;
        try {
            status = db.get(null, k, v, LockMode.READ_UNCOMMITTED);
        } catch (DatabaseException exn) {
            logger.warn("could not access database", exn);
            return Collections.emptyList();
        }

        if (OperationStatus.SUCCESS == status) {
            byte[] data = v.getData();

            return getValues(host, v.getData());
        } else {
            return Collections.emptyList();
        }
    }

    private String getVersion(Database db)
    {
        DatabaseEntry k = new DatabaseEntry(VERSION_KEY);
        DatabaseEntry v = new DatabaseEntry();
        try {
            OperationStatus s = db.get(null, k, v, LockMode.READ_UNCOMMITTED);
            if (OperationStatus.SUCCESS == s) {
                return new String(v.getData());
            } else {
                return null;
            }
        } catch (DatabaseException exn) {
            return null;
        }
    }

    private void setVersion(Database db, String version)
    {
        if (null != version) {
            try {
                DatabaseEntry k = new DatabaseEntry(VERSION_KEY);
                DatabaseEntry v = new DatabaseEntry(version.getBytes());
                db.put(null, k, v);
            } catch (DatabaseException exn) {
                logger.warn("could not set version", exn);
            }
        }
    }

    private void update()
    {
        synchronized (DB_LOCKS) {
            if (DB_LOCKS.contains(dbLock)) {
                return;
            } else {
                DB_LOCKS.add(dbLock);
            }
        }

        BufferedReader br = null;
        try {
            String oldVersion = getVersion(db);

            String v = null == oldVersion ? "1:1" : oldVersion.replace(".", ":");
            URL url = new URL(baseUrl + "/update?version=" + dbName + ":" + v);
            logger.info("updating from URL: " + url);

            InputStream is = url.openStream();
            InputStreamReader isr = new InputStreamReader(is);
            br = new BufferedReader(isr);

            String line = br.readLine();
            if (null == line) {
                logger.info("no update");
                return;
            }

            String newVersion;
            boolean update;
            Matcher matcher = VERSION_PATTERN.matcher(line);
            if (matcher.find()) {
                newVersion = matcher.group(1);
                update = null != matcher.group(2);
            } else {
                logger.info("no update");
                return;
            }

            logger.info("updating: " + dbLock + " from: " + oldVersion
                        + " to: " + newVersion);

            if (!update) {
                clearDatabase();
            }

            updateDatabase(db, br);

            setVersion(db, newVersion);

            db.getEnvironment().sync();
        } catch (DatabaseException exn) {
            logger.warn("could not update database", exn);
        } catch (IOException exn) {
            logger.warn("could not update database", exn);
        } finally {
            synchronized (dbLock) {
                DB_LOCKS.remove(dbLock);
            }
        }
    }

    private void clearDatabase()
    {
        Cursor c = null;
        try {
            DatabaseEntry k = new DatabaseEntry();
            DatabaseEntry v = new DatabaseEntry();

            c = db.openCursor(null, null);
            while (OperationStatus.SUCCESS == c.getNext(k, v, LockMode.DEFAULT)) {
                c.delete();
            }
        } catch (DatabaseException exn) {
            logger.warn("could not clear database");
        } finally {
            if (null != c) {
                try {
                    c.close();
                } catch (DatabaseException exn) {
                    logger.warn("could not close cursor", exn);
                }
            }
        }
    }
}
