{
    "uniqueId": "wan-balancer-Gr4P27bSin",
    "category": "WAN Balancer",
    "description": "The number of bytes destined to each interface.",
    "displayOrder": 200,
    "enabled": true,
    "javaClass": "com.untangle.node.reports.ReportEntry",
    "orderByColumn": "server_intf",
    "orderDesc": false,
    "units": "bytes",
    "pieGroupColumn": "server_intf",
    "pieSumColumn": "sum(s2p_bytes + p2s_bytes)",
    "readOnly": true,
    "table": "sessions",
    "title": "Bytes By Interface",
    "type": "PIE_GRAPH"
}

