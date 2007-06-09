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
import java.nio.ByteBuffer;


/**
 * Base class for Observers of mechanisms
 * which support privacy/integrity protection.
 * <br><br>
 * By default, this class does not inspect the
 * protocol yet advertizes that integrity and
 * privacy mey result from the exchange.
 */
abstract class PrivIntObserver
    extends SASLObserver {


    PrivIntObserver(String mechName, int maxMessageSz) {
        super(mechName, true, true, maxMessageSz);
    }

    @Override
    public FeatureStatus exchangeUsingPrivacy() {
        return FeatureStatus.UNKNOWN;
    }

    @Override
    public FeatureStatus exchangeUsingIntegrity() {
        return FeatureStatus.UNKNOWN;
    }

    @Override
    public FeatureStatus exchangeAuthIDFound() {
        return FeatureStatus.UNKNOWN;
    }

    @Override
    public String getAuthID() {
        return null;
    }

    @Override
    public FeatureStatus exchangeComplete() {
        return FeatureStatus.UNKNOWN;
    }

    @Override
    public boolean initialClientData(ByteBuffer buf) {
        return false;
    }

    @Override
    public boolean clientData(ByteBuffer buf) {
        return false;
    }

    @Override
    public boolean serverData(ByteBuffer buf) {
        return false;
    }

}
