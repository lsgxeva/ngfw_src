/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.untangle.node.sasl;
import static com.untangle.node.util.ASCIIUtil.*;


/**
 * Observer for SKEY (RFC 2222) mechanism.
 */
class SKEYObserver
    extends InitialIDObserver {

    static final String[] MECH_NAMES = new String[] {
        "SKEY".toLowerCase()
    };

    SKEYObserver() {
        super(MECH_NAMES[0], DEF_MAX_MSG_SZ);
    }
}
