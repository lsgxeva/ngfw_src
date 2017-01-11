// test
Ext.define('Ung.Application', {
    extend: 'Ext.app.Application',
    namespace: 'Ung',

    autoCreateViewport: false,
    name: 'Ung',

    rpc: null,

    controllers: ['Global'],

    defaultToken : '',

    // mainView: 'Ung.view.main.Main',

    init: function () {
        console.log('init');
        console.timeEnd('resources');
        // Ext.get('app-loader').destroy();
    },

    launch: function () {
        console.log('launch');
        var me = this;
        Rpc.rpc = me.rpc;

        Ext.getStore('policies').loadData(me.rpc.appsViews);

        Ung.util.Metrics.start();

        Ext.Deferred.parallel([
            Rpc.getDashboardSettings,
            Rpc.getReports,
            Rpc.getUnavailableApps
        ]).then(function (result) {
            Ext.get('app-loader').destroy();
            Ung.dashboardSettings = result[0];
            Ext.getStore('widgets').loadData(result[0].widgets.list);
            if (result[1]) {
                Ext.getStore('reports').loadData(result[1].list);
            }
            if (result[2]) {
                Ext.getStore('unavailableApps').loadRawData(result[2].map);
            }
            me.loadMainView();
            //console.log(reports);
            //this.setWidgets();
        }, function (exception) {
            console.log(exception);
        });



        // Ext.get('app-message').setHtml('Reports ...');

        // need to check if reports enabled an load it if so
        // console.time('dash');
        // Rpc.loadDashboardSettings().then(function(settings) {
        //     console.timeEnd('dash');
        //     Ext.getStore('widgets').loadData(settings.widgets.list);

        //     if (me.rpc.nodeManager.node('untangle-node-reports')) {
        //         Rpc.loadReports().then(function (reports) {
        //             Ext.getStore('reports').loadData(reports.list);
        //             me.loadMainView();
        //         });
        //     } else {
        //         me.loadMainView();
        //     }
        //     // me.loadMainView();
        //     // me.getView().setSettings(settings);
        //     // if (vm.get('reportsInstalled')) {
        //     //     // load unavailable apps needed for showing the widgets
        //     //     console.time('unavailApps');
        //     //     rpc.reportsManager.getUnavailableApplicationsMap(function (result, ex) {
        //     //         if (ex) { Ung.Util.exceptionToast(ex); return false; }

        //     //         Ext.getStore('unavailableApps').loadRawData(result.map);
        //     //         Ext.getStore('widgets').loadData(settings.widgets.list);
        //     //         console.timeEnd('unavailApps');
        //     //         me.loadWidgets();
        //     //     });
        //     // } else {
        //     //     Ext.getStore('widgets').loadData(settings.widgets.list);
        //     //     me.loadWidgets();
        //     // }
        //     // me.populateMenus();
        // });

        // uncomment this to retreive the class load order inside browser
        // Ung.Util.getClassOrder();
    },

    loadMainView: function () {
        Ung.util.Metrics.start();
        try {
            Ung.app.setMainView('Ung.view.main.Main');
        } catch (ex) {
            console.error(ex);
            Ung.Util.exceptionToast(ex);
            return;
        }

        // start metrics
        // Ext.get('app-loader').destroy();

        // destroy app loader
        // Ext.get('app-loader').addCls('removing');
        // Ext.Function.defer(function () {
        //     Ext.get('app-loader').destroy();
        // }, 500);
    }
});
