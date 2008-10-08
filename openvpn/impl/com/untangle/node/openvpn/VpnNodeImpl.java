/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.untangle.node.openvpn;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

import com.untangle.uvm.IntfConstants;
import com.untangle.uvm.LocalUvmContext;
import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.MailSender;
import com.untangle.uvm.logging.EventManager;
import com.untangle.uvm.message.BlingBlinger;
import com.untangle.uvm.message.Counters;
import com.untangle.uvm.message.LocalMessageManager;
import com.untangle.uvm.networking.IPNetwork;
import com.untangle.uvm.node.HostAddress;
import com.untangle.uvm.node.HostName;
import com.untangle.uvm.node.IPaddr;
import com.untangle.uvm.node.NodeException;
import com.untangle.uvm.node.NodeStartException;
import com.untangle.uvm.node.NodeState;
import com.untangle.uvm.node.NodeStopException;
import com.untangle.uvm.node.UnconfiguredException;
import com.untangle.uvm.node.ValidateException;
import com.untangle.uvm.node.Validator;
import com.untangle.uvm.node.script.ScriptRunner;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.util.TransactionWork;
import com.untangle.uvm.util.XMLRPCUtil;
import com.untangle.uvm.vnet.AbstractNode;
import com.untangle.uvm.vnet.Affinity;
import com.untangle.uvm.vnet.Fitting;
import com.untangle.uvm.vnet.PipeSpec;
import com.untangle.uvm.vnet.SoloPipeSpec;


public class VpnNodeImpl extends AbstractNode
    implements VpnNode
{
    private static final String TRAN_NAME    = "openvpn";
    private static final String WEB_APP      = TRAN_NAME;
    private static final String WEB_APP_PATH = "/" + WEB_APP;

    private static final String CLEANUP_SCRIPT = Constants.SCRIPT_DIR + "/cleanup";

    private static final HostAddress EMPTY_HOST_ADDRESS = new HostAddress( HostName.getEmptyHostName());

    private static final String SERVICE_NAME = "openvpn";

    /* Expire these links after an hour */
    private static final long ADMIN_DOWNLOAD_CLIENT_TIMEOUT = 1000l * 60 * 60;

    private final Logger logger = Logger.getLogger( VpnNodeImpl.class );

    private boolean isWebAppDeployed = false;

    private final SoloPipeSpec pipeSpec;
    private final SoloPipeSpec[] pipeSpecs;

    private final Random random = new Random();

    private final OpenVpnManager openVpnManager = new OpenVpnManager();
    private final CertificateManager certificateManager = new CertificateManager();
    private final AddressMapper addressMapper = new AddressMapper();
    private final OpenVpnMonitor openVpnMonitor;
    private final OpenVpnCaretaker openVpnCaretaker = new OpenVpnCaretaker();
    private final PhoneBookAssistant assistant;

    private final EventHandler handler;

    VpnSettings settings;

    private Sandbox sandbox = null;

    private final BlingBlinger passBlinger;
    private final BlingBlinger blockBlinger;
    private final BlingBlinger connectBlinger;
    
    private String adminDownloadClientKey = null;
    private long   adminDownloadClientExpiration = 0l;

    // constructor ------------------------------------------------------------


    public VpnNodeImpl()
    {
        this.handler          = new EventHandler( this );
        this.openVpnMonitor   = new OpenVpnMonitor( this );
        this.assistant        = new PhoneBookAssistant();

        /* Have to figure out pipeline ordering, this should always
         * next to towards the outside, then there is OpenVpn and then Nat */
        this.pipeSpec = new SoloPipeSpec
            ( TRAN_NAME, this, handler, Fitting.OCTET_STREAM, Affinity.CLIENT,
              SoloPipeSpec.MAX_STRENGTH - 2);
        this.pipeSpecs = new SoloPipeSpec[] { pipeSpec };


        LocalMessageManager lmm = LocalUvmContextFactory.context().localMessageManager();
        Counters c = lmm.getCounters(getTid());
        blockBlinger = c.addActivity("block", I18nUtil.marktr("Block Connection"), null, I18nUtil.marktr("BLOCK"));
        passBlinger = c.addActivity("pass", I18nUtil.marktr("Pass Connection"), null, I18nUtil.marktr("PASS"));
        connectBlinger = c.addActivity("connect", I18nUtil.marktr("Connect"), null, I18nUtil.marktr("CONNECT"));
        lmm.setActiveMetricsIfNotSet(getTid(), blockBlinger, passBlinger, connectBlinger);
    }

    @Override public void initializeSettings()
    {
        VpnSettings settings = new VpnSettings( this.getTid());
        logger.info( "Initializing Settings... to unconfigured" );

        setVpnSettings( settings );

        /* Stop the open vpn monitor(I don't think this actually does anything, rbs) */
        this.openVpnMonitor.stop();
        this.openVpnCaretaker.stop();
    }

    // VpnNode methods --------------------------------------------------
    public void setVpnSettings( final VpnSettings newSettings )
    {
        fixGroups(newSettings);

        /* Attempt to assign all of the clients addresses only if in server mode */
        try {
            if ( !newSettings.isUntanglePlatformClient() && newSettings.isConfigured()) {
                addressMapper.assignAddresses( newSettings );
            }
        } catch ( NodeException exn ) {
            logger.warn( "Could not assign client addresses, continuing", exn );
        }

        if ( !newSettings.isUntanglePlatformClient()) {
            /* Update the status/generate all of the certificates for clients */
            this.certificateManager.updateCertificateStatus( newSettings );
        }

        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork( Session s )
                {
                    if (null != newSettings.getId()) {
                        s.update(newSettings);
                    }

                    /* Delete all of the old settings */
                    Query q = null;
                    Long newSettingsId = newSettings.getId();
                    if ( newSettingsId == null ) {
                        q = s.createQuery( "from VpnSettings ts where ts.tid = :tid" );
                    } else {
                        q = s.createQuery( "from VpnSettings ts where ts.tid = :tid and ts.id != :id" );
                        q.setParameter( "id", newSettingsId );
                    }

                    q.setParameter( "tid", getTid());

                    for ( Object o : q.list()) s.delete((VpnSettings)o );

                    /* Save the new settings */
                    VpnNodeImpl.this.settings = (VpnSettings)s.merge( newSettings );
                    return true;
                }
            };

        getNodeContext().runTransaction( tw );

        try {
            if ( getRunState() == NodeState.RUNNING ) {
                /* This stops then starts openvpn */
                this.openVpnManager.configure( this.settings );
                this.openVpnManager.restart( this.settings );
                this.handler.configure( this.settings );

                if ( this.settings.isUntanglePlatformClient()) {
                    this.openVpnMonitor.disable();
                    this.openVpnCaretaker.start();
                } else {
                    this.openVpnMonitor.enable();
                }

                try {
                    /* XXX Update the iptables rules, this should be a call in the network manager XXX */
                    XMLRPCUtil.NullAsyncCallback callback = new XMLRPCUtil.NullAsyncCallback( this.logger );
                    /* Make an asynchronous request */
                    XMLRPCUtil.getInstance().callAlpaca( XMLRPCUtil.CONTROLLER_UVM, "generate_rules",
                                                         callback );
                } catch ( Exception e ) {
                    logger.error( "Error while generating iptables rules", e );
                }
            }

            this.assistant.configure( this.settings, getRunState() == NodeState.RUNNING );
        } catch ( NodeException exn ) {
            logger.error( "Could not save VPN settings", exn );
        }
    }
    
    private void fixGroups(VpnSettings newSettings) {
        Map<Long,VpnGroup> resolveGroupMap = new HashMap<Long,VpnGroup>();
        for ( VpnGroup group : newSettings.getGroupList()) {
            resolveGroupMap.put( group.getId(), group );
        }
        
        fixGroups(newSettings.getClientList(), resolveGroupMap);
        fixGroups(newSettings.getSiteList(), resolveGroupMap);
    }
    
    private void fixGroups(List newClientList, Map<Long,VpnGroup> resolveGroupMap) {
        for (VpnClientBase client : (List<VpnClientBase>) newClientList) {
            Long id = client.getGroup().getId();
            VpnGroup newGroup = resolveGroupMap.get(id);
            if (newGroup != null) {
                client.setGroup(newGroup);
            }
        }
    }

    public VpnSettings getVpnSettings()
    {
        /* XXXXXXXXXXXXXXXXXXXX This is not really legit, done so the schema doesn't have to be
         * written for a little while */
        if ( this.settings == null ) this.settings = new VpnSettings( this.getTid());

        return this.settings;
    }

    public VpnClientBase generateClientCertificate( VpnSettings settings, VpnClientBase client )
    {
        try {
            certificateManager.createClient( client );
        } catch ( NodeException e ) {
            logger.error( "Unable to create a client certificate", e );
        }

        return client;
    }

    public VpnClientBase revokeClientCertificate( VpnSettings settings, VpnClientBase client )
    {
        try {
            certificateManager.revokeClient( client );
        } catch ( NodeException e ) {
            logger.error( "Unable to revoke a client certificate", e );
        }

        return client;
    }

    private void distributeAllClientFiles( VpnSettings settings ) throws NodeException
    {
        for ( VpnClientBase client : settings.getCompleteClientList()) {
            if ( !client.getDistributeClient()) continue;
            distributeRealClientConfig( client );
        }
    }

    public void distributeClientConfig( VpnClientBase client )
        throws NodeException
    {
        /* Retrieve the client configuration object from the settings */
        boolean foundRealClient = false;
        for ( VpnClientBase realClient : settings.getCompleteClientList()) {
            if ( client.getInternalName().equals( realClient.getInternalName())) {
                realClient.setDistributionEmail( client.getDistributionEmail());
                client = realClient;
                foundRealClient = true;
                break;
            }
        }

        if ( foundRealClient ) distributeRealClientConfig( client );
        else throw new NodeException( "Attempt to distribute an unsaved client" );
    }

    /** The client config is the same client configuration object that is in settings */
    private void distributeRealClientConfig( final VpnClientBase client )
        throws NodeException
    {
        /* this client may already have a key, the key may have
         * already been created. */


        this.certificateManager.createClient( client );

        final String email = client.getDistributionEmail();
        String method = "download";

        if ( email != null ) {
            /* Generate a random key for email distribution */
            String key = client.getDistributionKey();

            /* Only generate a new key if the key has been modified */
            if ( key == null || key.length() == 0 ) {
                if ( client.isUntanglePlatform()) {
                    /* Use a shorter key for edge guard clients since they have to be
                     * typed in manually */
                    key = String.format( "%08x", random.nextInt());
                } else {
                    key = String.format( "%08x%08x", random.nextInt(), random.nextInt());
                }
            }
            client.setDistributionKey( key );

            method = "email";
        }

        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork( Session s )
                {
                    s.merge( client );
                    return true;
                }
            };

        getNodeContext().runTransaction( tw );

        this.openVpnManager.writeClientConfigurationFiles( settings, client, method );

        if ( email != null ) distributeClientConfigEmail( client, email );
    }

    private void distributeClientConfigEmail( VpnClientBase client, String email )
        throws NodeException
    {
        try {
            String subject = "OpenVPN Client";
            LocalUvmContext uvm = LocalUvmContextFactory.context();
            MailSender mailSender = uvm.mailSender();

            /* Read in the contents of the file */
            FileReader fileReader = new FileReader( Constants.PACKAGES_DIR + "/mail-" +
                                                    client.getInternalName() + ".eml" );
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int rs;
            while (( rs = fileReader.read( buf )) > 0 ) sb.append( buf, 0, rs );

            try {
                fileReader.close();
            } catch ( IOException e ) {
                logger.warn( "Unable to close file", e );
            }

            String recipients[] = { email };

            mailSender.sendHtmlMessage( recipients, subject, sb.toString());
        } catch ( Exception e ) {
            logger.warn( "Error distributing client key", e );
            throw new NodeException( e );
        }
    }

    /* Get the common name for the key, and clear it if it exists */
    public synchronized String lookupClientDistributionKey( String key, IPaddr clientAddress )
    {
        if (logger.isDebugEnabled()) {
            logger.debug( "Looking up client for key: " + key );
        }

        /* Could use a hash map, but why bother ? */
        for ( final VpnClientBase client : ((List<VpnClientBase>)this.settings.getCompleteClientList())) {
            if ( lookupClientDistributionKey( key, clientAddress, client )) return client.getInternalName();
        }

        return null;
    }

    /* Returns a URL to use to download the admin key. */
    public String getAdminDownloadLink( String clientName, ConfigFormat format )
        throws NodeException
    {
        boolean foundClient = false;
        for ( final VpnClientBase client : this.settings.getCompleteClientList()) {
            if ( !client.getInternalName().equals( clientName ) &&
                 !client.getName().equals( clientName )) continue;

            clientName = client.getInternalName();
            
            /* Clear out the distribution email */
            client.setDistributionEmail( null );
            distributeRealClientConfig( client );
            foundClient = true;
            break;
        }

        if ( !foundClient ) {
            throw new NodeException( "Unable to download unsaved clients <" + clientName +">" );
        }

        generateAdminClientKey();

        String fileName = null;
        switch ( format ) {
        case SETUP_EXE : fileName = "setup.exe";  break;
        case ZIP: fileName = "config.zip"; break;
        }
        
        return WEB_APP_PATH + "/" + fileName + 
            "?" + Constants.ADMIN_DOWNLOAD_CLIENT_KEY + "=" + 
            URLEncoder.encode( this.adminDownloadClientKey ) + 
            "&" + Constants.ADMIN_DOWNLOAD_CLIENT_PARAM + "=" + URLEncoder.encode( clientName );
    }

    /* Returns true if this is the correct authentication key for
     * downloading keys as the administrator */
    public boolean isAdminKey( String key )
    {
        long now = System.currentTimeMillis();
        
        /* This is designed to protect against clock changes and keys create way in the future */
        if (( this.adminDownloadClientExpiration < now ) ||
            ( this.adminDownloadClientExpiration > ( now + ( ADMIN_DOWNLOAD_CLIENT_TIMEOUT * 2 )))) {
            this.adminDownloadClientExpiration = 0;
            this.adminDownloadClientKey = null;
        }
            
        if (( this.adminDownloadClientKey == null ) || !this.adminDownloadClientKey.equals( key )) {
            return false;
        }
    
        return true;
    }

    public void addClientDistributionEvent( IPaddr clientAddress, String clientName )
    {
        this.openVpnMonitor.
            addClientDistributionEvent( new ClientDistributionEvent( clientAddress, clientName ));
    }

    private boolean lookupClientDistributionKey( String key, IPaddr clientAddress, final VpnClientBase client )
    {
        String clientKey = client.getDistributionKey();

        /* XXX, possible check if it is live ??? */

        if ( clientKey != null ) clientKey = clientKey.trim();
        if (logger.isDebugEnabled()) {
            logger.debug( "Checking: " + clientKey );
        }

        if ( clientKey == null || clientKey.length() == 0 ) return false;
        if ( !clientKey.equalsIgnoreCase( key )) return false;

        client.setDistributionKey( null );

        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork( Session s )
                {
                    s.merge( client );
                    return true;
                }
            };

        getNodeContext().runTransaction( tw );

        return true;
    }

    private synchronized void deployWebApp()
    {
        if ( !isWebAppDeployed ) {
            if (null != LocalUvmContextFactory.context().appServerManager().loadInsecureApp( WEB_APP_PATH, WEB_APP)) {
                logger.debug( "Deployed openvpn web app" );
            }
            else logger.warn( "Unable to deploy openvpn web app" );
        }
        isWebAppDeployed = true;

        /* unregister the service with the UVM */
        /* Make sure to leave this in because it reloads the iptables rules. */
        LocalUvmContextFactory.context().networkManager().registerService( SERVICE_NAME );
    }

    private synchronized void unDeployWebApp()
    {
        if ( isWebAppDeployed ) {
            if( LocalUvmContextFactory.context().appServerManager().unloadWebApp(WEB_APP_PATH )) {
                logger.debug( "Unloaded openvpn web app" );
            } else logger.warn( "Unable to unload openvpn web app" );
        }
        isWebAppDeployed = false;

        /* unregister the service with the UVM */
        /* Make sure to leave this in because it reloads the iptables rules. */
        LocalUvmContextFactory.context().networkManager().unregisterService( SERVICE_NAME );
    }

    // AbstractNode methods ----------------------------------------------

    @Override protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    // lifecycle --------------------------------------------------------------
    @Override protected void preInit( final String[] args ) throws NodeException
    {
        super.preInit( args );

        try {
            this.openVpnMonitor.start();
        } catch ( Exception e ) {
            logger.warn( "Unable to start openvpn monitor." );
        }

        // XXX ALPACA_INTEGRATION
        /* Initially use tun0, even though it could eventually be configured to the tap interface  */
//         try {
//             LocalUvmContextFactory.context().localIntfManager().registerIntf( "tun0", IntfConstants.VPN_INTF );
//         } catch ( ArgonException e ) {
//             throw new NodeException( "Unable to register VPN interface", e );
//         }
    }

    @Override protected void postInit(final String[] args) throws NodeException
    {
        super.postInit( args );

        /* register the assistant with the phonebook */
        LocalUvmContextFactory.context().localPhoneBook().registerAssistant( this.assistant );

        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork( Session s )
                {
                    Query q = s.createQuery( "from VpnSettings ts where ts.tid = :tid" );

                    q.setParameter( "tid", getTid());

                    settings = (VpnSettings)q.uniqueResult();
                    return true;
                }
            };
        getNodeContext().runTransaction( tw );

        deployWebApp();
    }

    @Override protected void preStart() throws NodeStartException
    {
        super.preStart();

        if ( this.settings == null ) {
            String[] args = {""};
            try {
                postInit( args );
            } catch ( NodeException e ) {
                throw new NodeStartException( "post init", e );
            }

            if ( this.settings == null ) initializeSettings();
        }

        /* Don't start if openvpn cannot be configured */
        if ( !settings.isConfigured()) throw new UnconfiguredException( "Openvpn is not configured" );

        try {
            settings.validate();

            this.openVpnManager.configure( settings );
            this.handler.configure( settings );
            this.openVpnManager.restart( settings );
            this.assistant.configure( settings, true );
        } catch( Exception e ) {
            try {
                this.openVpnManager.stop();
            } catch ( Exception stopException ) {
                logger.error( "Unable to stop the openvpn process", stopException );
            }
            throw new UnconfiguredException( e );
        }

        deployWebApp();

        /* Only start the monitor for servers */
        if ( settings.isUntanglePlatformClient()) {
            this.openVpnMonitor.disable();
            this.openVpnCaretaker.start();
        } else {
            this.openVpnMonitor.enable();
        }
    }

    @Override protected void postStop() throws NodeStopException
    {
        super.postStop();
    }

    @Override protected void preStop() throws NodeStopException {
        super.preStop();

        try {
            this.openVpnMonitor.disable();
            this.openVpnCaretaker.stop();
        } catch ( Exception e ) {
            logger.warn( "Error disabling openvpn monitor", e );
        }

        try {
            this.openVpnManager.stop();
        } catch ( NodeException e ) {
            logger.warn( "Error stopping openvpn manager", e );
        }

        this.assistant.configure( settings, false );

        /* unregister the service with the UVM */
        /* Make sure to leave this in because it reloads the iptables rules. */
        LocalUvmContextFactory.context().networkManager().unregisterService( SERVICE_NAME );
    }

    @Override protected void postDestroy() throws NodeException
    {
        super.postDestroy();

        try {
            this.openVpnMonitor.stop();
            this.openVpnCaretaker.stop();
        } catch ( Exception e ) {
            logger.warn( "Error stopping openvpn monitor", e );
        }

        LocalUvmContextFactory.context().localPhoneBook().unregisterAssistant( this.assistant );

        unDeployWebApp();
    }

    @Override protected void uninstall()
    {
        super.uninstall();

        unDeployWebApp();

        try {
            LocalUvmContextFactory.context().localIntfManager().unregisterIntf( IntfConstants.VPN_INTF );
        } catch ( Exception e ) {
            /* There is nothing else to do but print out the message */
            logger.error( "Unable to deregister vpn interface", e );
        }

        try {
            ScriptRunner.getInstance().exec( CLEANUP_SCRIPT );
        } catch ( Exception e ) {
            logger.error( "Unable to cleanup data.", e );
        }
    }

    // private methods -------------------------------------------------------

    // XXX soon to be deprecated ----------------------------------------------

    public Object getSettings()
    {
        return getVpnSettings();
    }

    public void setSettings( Object settings )
    {
        setVpnSettings((VpnSettings)settings);
    }

    public ConfigState getConfigState()
    {
        if ( settings == null || !settings.isConfigured()) return ConfigState.UNCONFIGURED;

        if ( settings.isUntanglePlatformClient()) return ConfigState.CLIENT;

        return ( settings.isBridgeMode() ? ConfigState.SERVER_BRIDGE : ConfigState.SERVER_ROUTE );
    }

    public HostAddress getVpnServerAddress()
    {
        /* Return the empty address */
        if (( this.settings == null ) || ( getConfigState() != ConfigState.CLIENT )) {
            logger.info( "non-client state, and request for server address" );
            return EMPTY_HOST_ADDRESS;
        }

        HostAddress address = this.settings.getServerAddress();

        if ( address == null ) {
            logger.info( "null, host address, returning empty address." );
            return EMPTY_HOST_ADDRESS;
        }

        return address;
    }

    public void startConfig( ConfigState state ) throws ValidateException
    {
        if ( state == ConfigState.UNCONFIGURED || state == ConfigState.SERVER_BRIDGE ) {
            throw new ValidateException( "Cannot run wizard for the selected state: " + state );
        }

        this.sandbox = new Sandbox( state );

        if ( state == ConfigState.SERVER_ROUTE ) {
            this.sandbox.autoDetectAddressPool();
            this.sandbox.autoDetectExportList();
        }
    }

    public String getAdminClientUploadLink()
    {
        generateAdminClientKey();
        
        return WEB_APP_PATH + "/clientSetup?" +
            Constants.ADMIN_DOWNLOAD_CLIENT_KEY + "=" + URLEncoder.encode( this.adminDownloadClientKey );
    }

    public void completeConfig() throws Exception
    {
        VpnSettings newSettings = this.sandbox.completeConfig( this.getTid());

        /* Generate new settings */
        if ( newSettings.isUntanglePlatformClient()) {
            /* Finish the configuration for clients, nothing left to do,
             * it is all done at download time */
        } else {
            /* Try to cleanup the previous data */
            ScriptRunner.getInstance().exec( CLEANUP_SCRIPT );

            /* Create the base certificate and parameters */
            this.certificateManager.createBase( newSettings );

            /* Create clients certificates */
            this.certificateManager.createAllClientCertificates( newSettings );

            /* Distribute emails */
            distributeAllClientFiles( newSettings );
        }

        setVpnSettings( newSettings );

        /* No reusing the sanbox */
        this.sandbox = null;
    }

    public void installClientConfig( String path ) throws Exception
    {
        this.sandbox.installClientConfig( path );
    }
    
    public void downloadConfig( HostAddress address, int port, String key ) throws Exception
    {
        this.sandbox.downloadConfig( address, port, key );
    }

    public void generateCertificate( CertificateParameters parameters ) throws Exception
    {
        this.sandbox.generateCertificate( parameters );
    }

    public GroupList getAddressGroups() throws Exception
    {
        return this.sandbox.getGroupList();
    }

    public void setAddressGroups( GroupList parameters ) throws Exception
    {
        this.sandbox.setGroupList( parameters );
    }

    public ExportList getExportedAddressList()
    {
        return this.sandbox.getExportList();
    }

    public void setExportedAddressList( ExportList parameters ) throws Exception
    {
        this.sandbox.setExportList( parameters );
    }

    public void setClients( ClientList parameters) throws Exception
    {
        this.sandbox.setClientList( parameters );
    }

    public void setSites( SiteList parameters) throws Exception
    {
        this.sandbox.setSiteList( parameters );
    }

    public EventManager<ClientConnectEvent> getClientConnectEventManager()
    {
        return this.openVpnMonitor.getClientConnectLogger();
    }

    public EventManager<VpnStatisticEvent> getVpnStatisticEventManager()
    {
        return this.openVpnMonitor.getVpnStatsDistLogger();
    }

    public EventManager<ClientDistributionEvent> getClientDistributionEventManager()
    {
        return this.openVpnMonitor.getClientDistLogger();
    }

    public void incrementBlockCount()
    {
        blockBlinger.increment();
    }

    public void incrementPassCount()
    {
        passBlinger.increment();
    }

    public void incrementConnectCount()
    {
        connectBlinger.increment();
    }

    public Validator getValidator()
    {
        return new OpenVpnValidator();
    }

    private void generateAdminClientKey()
    {
        long now = System.currentTimeMillis();

        /* This is designed to protect against clock changes and keys create way in the future */
        if (( this.adminDownloadClientExpiration < now ) ||
            ( this.adminDownloadClientExpiration > ( now + ( ADMIN_DOWNLOAD_CLIENT_TIMEOUT * 2 )))) {
            this.adminDownloadClientExpiration = 0;
            this.adminDownloadClientKey = null;
        }
        
        if ( this.adminDownloadClientKey == null ) {
            this.adminDownloadClientKey = String.format( "%08x%08x", 
                                                         this.random.nextInt(), this.random.nextInt());
            this.adminDownloadClientExpiration = now + ADMIN_DOWNLOAD_CLIENT_TIMEOUT;
        }
    }
}
