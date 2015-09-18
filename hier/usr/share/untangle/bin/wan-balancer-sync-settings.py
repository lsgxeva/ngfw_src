#!/usr/bin/env python

# Sync Settings is takes the wan-balancer settings JSON file and "syncs" it to the 
# It reads through the settings and writes the appropriate files:
# /etc/untangle-netd/iptables-rules.d/330-wan-balancer-rules
#
# This script should be called after changing the settings file to "sync" the settings to the OS.

import sys
sys.path.insert(0, sys.path[0] + "/" + "../" + "../" + "../" + "lib/" + "python%s.%s/" % sys.version_info[:2])

import getopt
import signal
import os
import traceback
import json
import datetime

from   netd import *

class ArgumentParser(object):
    def __init__(self):
        self.file = None
        self.networkFile = None
        self.prefix = ''
        self.verbosity = 0
        self.disable = False

    def set_file( self, arg ):
        self.file = arg

    def set_networkFile( self, arg ):
        self.networkFile = arg

    def set_prefix( self, arg ):
        self.prefix = arg

    def increase_verbosity( self, arg ):
        self.verbosity += 1

    def set_disable( self, arg ):
        self.disable = True

    def parse_args( self ):
        handlers = {
            '-f' : self.set_file,
            '-n' : self.set_networkFile,
            '-p' : self.set_prefix,
            '-v' : self.increase_verbosity,
            '-d' : self.set_disable
        }

        try:
            (optlist, args) = getopt.getopt(sys.argv[1:], 'f:n:p:vd')
            for opt in optlist:
                handlers[opt[0]](opt[1])
            return args
        except getopt.GetoptError, exc:
            print exc
            printUsage()
            exit(1)

def printUsage():
    sys.stderr.write( """\
%s Usage:
  required args:
    -f <file>   : settings file to sync to OS
    -n <file>   : network settings file 
  optional args:
    -d          : disable wan-balancer (writes null ruleset)
    -p <prefix> : prefix to append to output files
    -v          : verbose (can be specified more than one time)
""" % sys.argv[0] )

# sanity check settings
def checkSettings( settings ):
    if settings.get('routeRules') == None or settings.get('routeRules').get('list') == None:
        raise Exception("Missing Route Rules")
    if settings.get('weights') == None:
        raise Exception("Missing WAN weights")
    return

def write_iptables_route_rule( file, route_rule, verbosity=0 ):
    if 'enabled' in route_rule and not route_rule['enabled']:
        return
    if 'matchers' not in route_rule or 'list' not in route_rule['matchers']:
        return
    if 'ruleId' not in route_rule:
        return

    if 'destinationWan' in route_rule:
        if int(route_rule.get('destinationWan')) == 0:
            target = " -j RETURN " 
        else:
            target = " -j MARK --set-mark 0x%04X/0x%04X " % ( int(route_rule.get('destinationWan'))<<8 ,0xff00) 
    else:
        print "ERROR: invalid route rule target: %s" + str(route_rule)
        return

    description = "Route Rule #%i" % int(route_rule['ruleId'])
    iptables_conditions = IptablesUtil.conditions_to_iptables_string( route_rule['matchers']['list'], description, verbosity );

    iptables_commands = [ "${IPTABLES} -t mangle -A wan-balancer-route-rules " + ipt + target for ipt in iptables_conditions ]
    iptables_commands_return = [ "${IPTABLES} -t mangle -A wan-balancer-route-rules " + ipt + " -j RETURN " for ipt in iptables_conditions ]

    file.write("# %s\n" % description);
    for cmd in iptables_commands:
        file.write(cmd + "\n")
    for cmd in iptables_commands_return:
        file.write(cmd + "\n")
    return


def write_iptables_file( file, verbosity=0 ):
    global parser

    file.write("#!/bin/dash");
    file.write("\n\n");

    file.write("## Auto Generated on %s\n" % datetime.datetime.now());
    file.write("## DO NOT EDIT. Changes will be overwritten.\n");
    file.write("\n");

    file.write("""
iptables_debug_onerror()
{
    # Ignore -N errors
    /sbin/iptables "$@" || {
        [ "${3}x" != "-Nx" ] && echo "[`date`] Failed: /sbin/iptables $@"
    }

    true
}

if [ -z "${IPTABLES}" ] ; then
    IPTABLES="iptables_debug_onerror"
fi

""")

    file.write("# Create (if needed) and flush restore-interface-marks, mark-src-intf, mark-dst-intf chains" + "\n");
    file.write("${IPTABLES} -t mangle -N wan-balancer-route-rules 2>/dev/null" + "\n");
    file.write("${IPTABLES} -t mangle -F wan-balancer-route-rules >/dev/null 2>&1" + "\n");
    file.write("\n");

    file.write("# Call chains from PREROUTING in mangle" + "\n");
    file.write("${IPTABLES} -t mangle -D PREROUTING -m conntrack --ctstate NEW -m comment --comment \"Run route rules\" -j wan-balancer-route-rules >/dev/null 2>&1" + "\n");

    if parser.disable:
        return

    file.write("${IPTABLES} -t mangle -I PREROUTING 3 -m conntrack --ctstate NEW -m comment --comment \"Run route rules\" -j wan-balancer-route-rules" + "\n");
    file.write("\n");

    try:
        file.write("\n")
        file.write("# Route Rules" + "\n")
        route_rules = settings.get('routeRules').get('list');
        for route_rule in route_rules:
            try:
                write_iptables_route_rule( file, route_rule, parser.verbosity );
            except Exception,e:
                traceback.print_exc(e)
    except Exception,e:
        traceback.print_exc(e)

def write_route_file( file, verbosity=0 ):
    global networkSettings, settings
    global parser

    file.write("#!/bin/dash");
    file.write("\n\n");

    file.write("## Auto Generated on %s\n" % datetime.datetime.now());
    file.write("## DO NOT EDIT. Changes will be overwritten.\n");
    file.write("\n");

    file.write("UNTANGLE_PRIORITY_BALANCE=\"900000\"" + "\n")
    file.write("\n");

    if parser.disable:
        file.write("ip rule del priority ${UNTANGLE_PRIORITY_BALANCE} >/dev/null 2>&1" + "\n")
        file.write("# echo ip rule del priority ${UNTANGLE_PRIORITY_BALANCE}" + "\n")
        file.write("exit 0" + "\n")
        return

    file.write("# For each non-zero-weight WAN considered up (an ip rule exists) include it in balancing." + "\n")
    file.write("ROUTE_STR=\"\"" + "\n")
    for intf in networkSettings.get('interfaces').get('list'):
        if intf.get('configType') == 'ADDRESSED' and intf.get('isWan'):
            interfaceId = int(intf.get('interfaceId'))
            weight = int(settings.get('weights')[intf.get('interfaceId') - 1])
            if ( weight != 0 ):
                file.write("if [ ! -z  \"`ip rule ls | grep fwmark | grep uplink.%i 2>/dev/null`\" ] ; then " % (interfaceId) + "\n")
                file.write("    ROUTE_STR=\"$ROUTE_STR `ip route show table uplink.%i | sed -e 's/default/nexthop/'` weight %i\"" % (interfaceId, weight) + "\n")
                file.write("fi" + "\n")
    file.write("\n");

    file.write("# If there are no routes (no up WANs) dont balance at all" + "\n")
    file.write("if [ -z \"$ROUTE_STR\" ] ; then" + "\n")
    file.write("    ip rule del priority ${UNTANGLE_PRIORITY_BALANCE} >/dev/null 2>&1" + "\n")
    file.write("    exit 0" + "\n")
    file.write("fi" + "\n")
    file.write("\n");

    file.write("# echo ip route replace table balance default scope global $ROUTE_STR" + "\n")
    file.write("ip route replace table balance default scope global $ROUTE_STR" + "\n")
    file.write("\n");

    file.write("# delete old priority" + "\n")
    file.write("ip rule del priority 366800 >/dev/null 2>&1" + "\n")
    file.write("\n");

    file.write("ip rule del priority ${UNTANGLE_PRIORITY_BALANCE} >/dev/null 2>&1" + "\n")
    file.write("ip rule add priority ${UNTANGLE_PRIORITY_BALANCE} lookup balance" + "\n")
    
    return

def check_settings( ):
    global networkSettings, settings

    totalWeight = 0.0
    for intf in networkSettings.get('interfaces').get('list'):
        if intf.get('configType') == 'ADDRESSED' and intf.get('isWan'):
            totalWeight = totalWeight + float(settings.get('weights')[intf.get('interfaceId') - 1])

   # If total weight is zero, change all weights to 1
    if totalWeight == 0.0:
        for intf in networkSettings.get('interfaces').get('list'):
            if intf.get('configType') == 'ADDRESSED' and intf.get('isWan'):
                settings.get('weights')[intf.get('interfaceId') - 1] = 1;





parser = ArgumentParser()
parser.parse_args()
settings = None

if parser.file == None or parser.networkFile == None:
    printUsage()
    sys.exit(1)

try:
    settingsFile = open(parser.file, 'r')
    settingsData = settingsFile.read()
    settingsFile.close()
    settings = json.loads(settingsData)
except IOError,e:
    print "Unable to read settings file: ",e
    exit(1)

try:
    networkSettingsFile = open(parser.networkFile, 'r')
    networkSettingsData = networkSettingsFile.read()
    networkSettingsFile.close()
    networkSettings = json.loads(networkSettingsData)
except IOError,e:
    print "Unable to read network settings file: ",e
    exit(1)

try:
    checkSettings(settings)
except Exception,e:
    traceback.print_exc(e)
    exit(1)

IptablesUtil.settings = networkSettings
NetworkUtil.settings = networkSettings

check_settings()

if parser.verbosity > 0: print "Syncing %s to system..." % parser.file

# Write 330-wan-balancer iptables file
filename = parser.prefix + "/etc/untangle-netd/iptables-rules.d/330-wan-balancer"
fileDir = os.path.dirname( filename )
if not os.path.exists( fileDir ):
    os.makedirs( fileDir )
    
file = open( filename, "w+" )

write_iptables_file( file, parser.verbosity )

file.flush()
file.close()
os.system("chmod a+x %s" % filename)

if parser.verbosity > 0: print "Wrote %s" % filename


# Write 040-wan-balancer post network hook file
filename = parser.prefix + "/etc/untangle-netd/post-network-hook.d/040-wan-balancer"
fileDir = os.path.dirname( filename )
if not os.path.exists( fileDir ):
    os.makedirs( fileDir )
    
file = open( filename, "w+" )

write_route_file( file, parser.verbosity )

file.flush()
file.close()
os.system("chmod a+x %s" % filename)

if parser.verbosity > 0: print "Wrote %s" % filename

