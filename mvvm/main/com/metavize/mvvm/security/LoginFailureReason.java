/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: HttpBlockerEvent.java 194 2005-04-06 19:13:55Z rbscott $
 */

package com.metavize.mvvm.security;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class LoginFailureReason implements Serializable
{

    public static final LoginFailureReason UNKNOWN_USER = new LoginFailureReason('U', "unknown user");
    public static final LoginFailureReason BAD_PASSWORD = new LoginFailureReason('P', "incorrect password");

    // For Future use:
    public static final LoginFailureReason DISABLED = new LoginFailureReason('D', "disabled by administrator");

    private static final Map INSTANCES = new HashMap();

    static {
        INSTANCES.put('U', UNKNOWN_USER);
        INSTANCES.put('P', BAD_PASSWORD);
        INSTANCES.put('D', DISABLED);
    }

    private final char key;
    private final String reason;

    private LoginFailureReason(char key, String reason)
    {
        this.key = key;
        this.reason = reason;
    }

    public char getKey()
    {
        return key;
    }

    public String toString()
    {
        return reason;
    }

    private Object readResolve()
    {
        return INSTANCES.get(reason);
    }

    public static LoginFailureReason getInstance(char key)
    {
        return (LoginFailureReason)INSTANCES.get(key);
    }

    // Object methods ---------------------------------------------------------

    public boolean equals(Object o)
    {
        if (!(o instanceof LoginFailureReason)) {
            return false;
        } else {
            LoginFailureReason lfr = (LoginFailureReason)o;
            return key == lfr.key;
        }
    }

    public int hashCode()
    {
        return (int)key;
    }
}
