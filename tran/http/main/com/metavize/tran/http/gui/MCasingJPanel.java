/*
 * MCasingJPanel.java
 *
 * Created on February 22, 2005, 1:10 PM
 */

package com.metavize.tran.http.gui;

import com.metavize.gui.transform.*;
import com.metavize.gui.util.*;

import com.metavize.mvvm.security.*;
import com.metavize.mvvm.*;
import com.metavize.mvvm.tran.*;
import com.metavize.tran.http.HttpSettings;

import java.awt.*;

/**
 *
 * @author  inieves
 */
public class MCasingJPanel extends com.metavize.gui.transform.MCasingJPanel {

    
    public MCasingJPanel(TransformContext transformContext) {
        super(transformContext);
        initComponents();
    }

    public void doSave(Object settings, boolean validateOnly) throws Exception {

        // HTTP ENABLED ///////////
        boolean isHttpEnabled = httpEnabledRadioButton.isSelected();
        
	// SAVE SETTINGS ////////////
	if( !validateOnly ){ 
            HttpSettings httpSettings = (HttpSettings) transformContext.transform().getSettings();
            httpSettings.setEnabled(isHttpEnabled);
            transformContext.transform().setSettings(httpSettings); 
        }

    }

    public void doRefresh(Object settings){
        
        // HTTP ENABLED /////////
        HttpSettings httpSettings = (HttpSettings) transformContext.transform().getSettings();
        boolean isHttpEnabled = httpSettings.isEnabled();
        if( isHttpEnabled )
            httpEnabledRadioButton.setSelected(true);
        else
            httpDisabledRadioButton.setSelected(true); 
    }
    
    
    private void initComponents() {//GEN-BEGIN:initComponents
        java.awt.GridBagConstraints gridBagConstraints;

        httpButtonGroup = new javax.swing.ButtonGroup();
        webJPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        httpEnabledRadioButton = new javax.swing.JRadioButton();
        httpDisabledRadioButton = new javax.swing.JRadioButton();

        setLayout(new java.awt.GridBagLayout());

        setMaximumSize(new java.awt.Dimension(563, 120));
        setMinimumSize(new java.awt.Dimension(563, 120));
        setPreferredSize(new java.awt.Dimension(563, 120));
        webJPanel.setLayout(new java.awt.GridBagLayout());

        webJPanel.setBorder(new javax.swing.border.TitledBorder(null, "Web Override", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 1, 16)));
        jLabel1.setFont(new java.awt.Font("Dialog", 0, 12));
        jLabel1.setText("Warning:  These settings should not be changed unless instructed to do so by support.");
        webJPanel.add(jLabel1, new java.awt.GridBagConstraints());

        httpButtonGroup.add(httpEnabledRadioButton);
        httpEnabledRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
        httpEnabledRadioButton.setText("<html><b>Allow</b> the processing of web traffic.  (This is the default settings)</html>");
        httpEnabledRadioButton.setFocusPainted(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        webJPanel.add(httpEnabledRadioButton, gridBagConstraints);

        httpButtonGroup.add(httpDisabledRadioButton);
        httpDisabledRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
        httpDisabledRadioButton.setText("<html><b>Stop</b> the processing of all web traffic.</html>");
        httpDisabledRadioButton.setFocusPainted(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        webJPanel.add(httpDisabledRadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        add(webJPanel, gridBagConstraints);

    }//GEN-END:initComponents
    

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup httpButtonGroup;
    public javax.swing.JRadioButton httpDisabledRadioButton;
    public javax.swing.JRadioButton httpEnabledRadioButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel webJPanel;
    // End of variables declaration//GEN-END:variables
    

}
