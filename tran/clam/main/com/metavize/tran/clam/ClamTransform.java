/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.clam;

import com.metavize.tran.virus.VirusTransformImpl;

public class ClamTransform extends VirusTransformImpl
{
    public ClamTransform()
    {
        super(new ClamScanner());
    }
}
