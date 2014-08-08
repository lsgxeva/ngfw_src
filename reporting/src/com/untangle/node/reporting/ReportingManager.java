/*
 * $Id$
 */
package com.untangle.node.reporting;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.json.JSONObject;

import com.untangle.node.reporting.items.DateItem;
import com.untangle.node.reporting.items.TableOfContents;
import com.untangle.node.reporting.items.ApplicationData;
import com.untangle.node.reporting.items.Highlight;

/**
 * Manages report generation.
 */
public interface ReportingManager
{
	TimeZone getTimeZone();
	
	List<DateItem> getDates();

    List<Highlight> getHighlights(Date d, int numDays);

    Date getReportsCutoff();

    TableOfContents getTableOfContents(Date d, int numDays);
    
    TableOfContents getTableOfContentsForHost(Date d, int numDays, String hostname);
    
    TableOfContents getTableOfContentsForUser(Date d, int numDays, String username);
    
    TableOfContents getTableOfContentsForEmail(Date d, int numDays, String email);

    ApplicationData getApplicationData(Date d, int numDays, String appName, String type, String value);
    
    ApplicationData getApplicationData(Date d, int numDays, String appName);

    ApplicationData getApplicationDataForUser(Date d, int numDays, String appName, String username);

    ApplicationData getApplicationDataForEmail(Date d, int numDays, String appName, String emailAddr);

    ApplicationData getApplicationDataForHost(Date d, int numDays, String appName, String hostname);

    ArrayList<JSONObject> getDetailData(Date d, int numDays, String appName, String detailName, String type, String value);

    ArrayList<JSONObject> getAllDetailData(Date d, int numDays, String appName, String detailName, String type, String value);
    
    Object getDetailDataResultSet(Date d, int numDays, String appName, String detailName, String type, String value);

    /**
     * Tests if reporting is enabled, that is if reports will be
     * generated nightly.  Currently this is the same thing as "is the
     * reporting node installed and turned on."
     *
     * @return true if reporting is enabled, false otherwise.
     */
    boolean isReportingEnabled();

    /**
     * Tests if reporting is enabled and reports have been generated
     * and are ready to view.  Currently this is the same thing as
     * "does the current symlink exist and contain a valid
     * reporting-node/sum-daily.html file."
     *
     * @return true if reports are available
     */
    boolean isReportsAvailable();
}
