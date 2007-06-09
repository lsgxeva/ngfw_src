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
package com.untangle.uvm.util;

public class QuarantineOutsideAccessValve extends OutsideValve
{
    public void QuarantineOutsideAccessValve()
    {
    }

    protected boolean isOutsideAccessAllowed()
    {
        return getAccessSettings().getIsOutsideQuarantineEnabled();
    }

    protected String outsideErrorMessage()
    {
        return "off-site access";
    }

    protected String httpErrorMessage()
    {
        return "standard access";
    }

}
