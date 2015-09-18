/**
 * $Id$
 */
package com.untangle.node.wan_balancer;

import java.util.List;
import java.io.Serializable;
import java.net.InetAddress;

import org.json.JSONObject;
import org.json.JSONString;
import org.apache.log4j.Logger;

/**
 * This in the implementation of a Route Rule
 *
 * A rule is basically a collection of RouteRuleMatchers (matchers)
 * and what to do if the matchers match (block, log, etc)
 */
@SuppressWarnings("serial")
public class RouteRule implements JSONString, Serializable
{
    private final Logger logger = Logger.getLogger(getClass());

    private List<RouteRuleMatcher> matchers;

    private Integer ruleId;
    private Boolean enabled;
    private String  description;
    private Integer destinationWan;
    
    public RouteRule() { }

    public RouteRule(boolean enabled, List<RouteRuleMatcher> matchers, Integer destinationWan, String description)
    {
        this.setMatchers(matchers);
        this.setEnabled(Boolean.valueOf(enabled));
        this.setDestinationWan(destinationWan);
        this.setDescription(description);
    }
    
    public List<RouteRuleMatcher> getMatchers() { return this.matchers; }
    public void setMatchers( List<RouteRuleMatcher> matchers ) { this.matchers = matchers; }

    public Integer getRuleId() { return this.ruleId; }
    public void setRuleId(Integer ruleId) { this.ruleId = ruleId; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled( Boolean enabled ) { this.enabled = enabled; }

    public String getDescription() { return description; }
    public void setDescription( String description ) { this.description = description; }

    public Integer getDestinationWan() { return destinationWan; }
    public void setDestinationWan( Integer destinationWan ) { this.destinationWan = destinationWan; }

    public String toJSONString()
    {
        JSONObject jO = new JSONObject(this);
        return jO.toString();
    }
}

