/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.mime;

/**
 * Class representing the raw data for a header,
 * as read from some MIME document.
 */
public class RawHeaderField {

  public final Line[] lines;
  public final int valueStartOffset;

  public RawHeaderField(Line[] lines,
    int valueStartOffset) {
    this.lines = lines;
    this.valueStartOffset = valueStartOffset;
  }

}