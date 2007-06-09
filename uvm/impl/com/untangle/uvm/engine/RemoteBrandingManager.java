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

package com.untangle.uvm.engine;

import com.untangle.uvm.BrandingManager;
import com.untangle.uvm.BrandingSettings;
import com.untangle.uvm.LocalBrandingManager;

class RemoteBrandingManagerImpl implements BrandingManager
{
    private final BrandingManager brandingManager;

    RemoteBrandingManagerImpl(LocalBrandingManager brandingManager)
    {
        this.brandingManager = brandingManager;
    }

    public BrandingSettings getBrandingSettings()
    {
        return this.brandingManager.getBrandingSettings();
    }

    public void setBrandingSettings(BrandingSettings bs)
    {
        this.brandingManager.setBrandingSettings(bs);
    }
        

}
