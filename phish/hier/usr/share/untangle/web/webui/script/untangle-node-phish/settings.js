Ext.define('Webui.untangle-node-phish.settings', {
    extend:'Ung.NodeWin',
    lastUpdate: null,
    lastCheck: null,
    signatureVersion: null,
    smtpData: null,
    spamData: null,
    emailPanel: null,
    webPanel: null,
    gridEmailEventLog: null,

    initComponent: function() {
        this.lastUpdate = this.getRpcNode().getLastUpdate();
        this.lastCheck = this.getRpcNode().getLastUpdateCheck();
        this.signatureVer = this.getRpcNode().getSignatureVersion();
        // build tabs
        this.buildEmail();
        this.buildEmailEventLog();
        // builds the tab panel with the tabs
        this.buildTabPanel([this.emailPanel, this.gridEmailEventLog]);
        this.callParent(arguments);
    },
    lookup: function(needle, haystack1, haystack2) {
        for (var i = 0; i < haystack1.length; i++) {
            if (haystack1[i] != undefined && haystack2[i] != undefined) {
                if (needle == haystack1[i]) {
                    return haystack2[i];
                }
                if (needle == haystack2[i]) {
                    return haystack1[i];
                }
            }
        }
        return null;
    },
    // Email Config Panel
    buildEmail: function() {
        this.smtpData = [['MARK', this.i18n._('Mark')], ['PASS', this.i18n._('Pass')],
                ['DROP', this.i18n._('Drop')], ['QUARANTINE', this.i18n._('Quarantine')]];
        this.spamData = [['MARK', this.i18n._('Mark')], ['PASS', this.i18n._('Pass')]];
        this.emailPanel = Ext.create('Ext.panel.Panel',{
            title: this.i18n._('Email'),
            name: 'Email',
            helpSource: 'phish_blocker_email',
            autoScroll: true,
            cls: 'ung-panel',
            items: [{
                xtype: 'fieldset',
                title: this.i18n._('SMTP'),
                defaults: {
                    width: 210
                },
                items: [{
                    xtype: 'checkbox',
                    boxLabel: this.i18n._('Scan SMTP'),
                    name: 'Scan SMTP',
                    hideLabel: true,
                    checked: this.settings.smtpConfig.scan,
                    handler: Ext.bind(function(elem, newValue) {
                        this.settings.smtpConfig.scan = newValue;
                    }, this)
                }, {
                    xtype: 'combo',
                    name: 'SMTP Action',
                    editable: false,
                    store:this.smtpData,
                    valueField: 'key',
                    displayField: 'name',
                    fieldLabel: this.i18n._('Action'),
                    queryMode: 'local',
                    value: this.settings.smtpConfig.msgAction,
                    listeners: {
                        "change": {
                            fn: Ext.bind(function(elem, newValue) {
                                this.settings.smtpConfig.msgAction = newValue;
                            }, this)
                        }
                    }
                }]
            }, {
                xtype: 'fieldset',
                title: this.i18n._('Note'),
                cls: 'description',
                html: this.i18n._('Phish Blocker email signatures were last updated') + ":&nbsp;&nbsp;&nbsp;&nbsp;" +
                    (this.lastUpdate != null && this.lastUpdate.time != 0 ? i18n.timestampFormat(this.lastUpdate): i18n._("never"))
            }]
        });
    },
    // Email Event Log
    buildEmailEventLog: function() {
        this.gridEmailEventLog = Ung.CustomEventLog.buildMailEventLog (this, 'EventLog', i18n._('Event Log'),
            'phish_blocker_event_log',
            ['time_stamp','c_client_addr','s_server_addr','subject','addr','sender','phish_action'],
            this.getRpcNode().getEventQueries);
    },
    
    afterSave: function()  {
        this.lastUpdate = this.getRpcNode().getLastUpdate();
        this.lastCheck = this.getRpcNode().getLastUpdateCheck();
        this.signatureVer = this.getRpcNode().getSignatureVersion();
    }

});
//# sourceURL=phish-settings.js