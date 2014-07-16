/**
 * $Id$
 */
package com.untangle.node.smtp;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import com.untangle.node.smtp.Response;
import com.untangle.node.smtp.SASLExchangeToken;
import com.untangle.node.token.MetadataToken;
import com.untangle.node.token.Token;
import com.untangle.node.token.UnparseResult;
import com.untangle.uvm.vnet.NodeTCPSession;

class SmtpClientUnparser extends SmtpUnparser
{
    private final Logger logger = Logger.getLogger(SmtpClientUnparser.class);

    SmtpClientUnparser( )
    {
        super( true );
    }

    @Override
    public void handleNewSession( NodeTCPSession session ) { }
    
    @Override
    protected UnparseResult doUnparse( NodeTCPSession session, Token token )
    {
        SmtpSharedState sharedState = (SmtpSharedState) session.globalAttachment( SHARED_STATE_KEY );

        // -----------------------------------------------------------
        if (token instanceof SASLExchangeToken) {
            logger.debug("Received SASLExchangeToken token");

            ByteBuffer buf = token.getBytes();

            if ( ! sharedState.isInSASLLogin() ) {
                logger.error("Received SASLExchangeToken without an open exchange");
            } else {
                switch ( sharedState.getSASLObserver().serverData(buf.duplicate() ) ) {
                    case EXCHANGE_COMPLETE:
                        logger.debug("SASL Exchange complete");
                        sharedState.closeSASLExchange();
                        break;
                    case IN_PROGRESS:
                        // Nothing to do
                        break;
                    case RECOMMEND_PASSTHRU:
                        logger.debug("Entering passthru on advice of SASLObserver");
                        declarePassthru( session );
                }
            }
            return new UnparseResult(buf);
        }

        // -----------------------------------------------------------
        if (token instanceof MetadataToken) {
            // Don't pass along metadata tokens
            logger.debug("Pass along Metadata token as nothing");
            return UnparseResult.NONE;
        }

        // -----------------------------------------------------------
        if (token instanceof Response) {
            Response resp = (Response) token;
            sharedState.responseReceived(resp);

            logger.debug("Passing response to client: " + resp.toDebugString());
        } else {
            logger.debug("Unparse token of type " + (token == null ? "null" : token.getClass().getName()));
        }

        return new UnparseResult(token.getBytes());
    }

}
