/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.spam;

import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.mvvm.tapi.IPSessionDesc;
import com.metavize.tran.token.TokenHandler;
import com.metavize.tran.token.TokenHandlerFactory;
import org.apache.log4j.Logger;
import com.metavize.tran.mail.papi.imap.ImapTokenStream;
import com.metavize.tran.mail.papi.MailExport;
import com.metavize.tran.mail.papi.MailExportFactory;
import com.metavize.tran.mail.papi.WrappedMessageGenerator;

public class SpamImapFactory
  implements TokenHandlerFactory {
  
  private static final Logger m_logger =
    Logger.getLogger(SpamImapFactory.class);

  private final SpamImpl m_impl;
  private final MailExport m_mailExport;

  private WrappedMessageGenerator m_inWrapper =
    new WrappedMessageGenerator(SpamSettings.IN_MOD_SUB_TEMPLATE, SpamSettings.IN_MOD_BODY_TEMPLATE);

  private WrappedMessageGenerator m_outWrapper =
    new WrappedMessageGenerator(SpamSettings.OUT_MOD_SUB_TEMPLATE, SpamSettings.OUT_MOD_BODY_TEMPLATE);  

  SpamImapFactory(SpamImpl impl) {
    m_impl = impl;
    /* XXX RBS I don't know if this will work */
    m_mailExport = MailExportFactory.factory().getExport( impl.getTid().getPolicy());    
  }


  public TokenHandler tokenHandler(TCPSession session) {
  
    boolean inbound = session.isInbound();
  
    SpamIMAPConfig config = inbound?
      m_impl.getSpamSettings().getIMAPInbound():
      m_impl.getSpamSettings().getIMAPOutbound();
  
    if(!config.getScan()) {
      m_logger.debug("Scanning disabled.  Return passthrough token handler");
      return new ImapTokenStream(session);
    }
  
    long timeout = inbound?m_mailExport.getExportSettings().getImapInboundTimeout():
      m_mailExport.getExportSettings().getImapOutboundTimeout();
  
    return new ImapTokenStream(session,
        new SpamImapHandler(
          session,
          timeout,
          timeout,
          m_impl,
          config,
          inbound?m_inWrapper:m_outWrapper)
      );
  }
}
