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

package com.untangle.uvm.node;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the runtime state of a node instance.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public enum NodeState
{
    /**
     * Instantiated, but not initialized. This is a transient state,
     * just after the main node class has been instantiated, but
     * before init has been called.
     */
    LOADED,

    /**
     * Initialized, but not running. The node instance enters
     * this state after it has been initialized, or when it is
     * stopped.
     */
    INITIALIZED,

    /**
     * Running.
     */
    RUNNING,

    /**
     * Destroyed, this instance should not be used.
     */
    DESTROYED,

    /**
     * Disabled.
     */
    DISABLED;
}
