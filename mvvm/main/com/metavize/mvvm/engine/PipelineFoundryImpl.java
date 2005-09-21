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

package com.metavize.mvvm.engine;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.metavize.mvvm.argon.ArgonAgent;
import com.metavize.mvvm.argon.IPSessionDesc;
import com.metavize.mvvm.argon.IntfConverter;
import com.metavize.mvvm.argon.PipelineDesc;
import com.metavize.mvvm.argon.SessionEndpoints;
import com.metavize.mvvm.policy.Policy;
import com.metavize.mvvm.policy.PolicyRule;
import com.metavize.mvvm.policy.SystemPolicyRule;
import com.metavize.mvvm.policy.UserPolicyRule;
import com.metavize.mvvm.tapi.CasingPipeSpec;
import com.metavize.mvvm.tapi.Fitting;
import com.metavize.mvvm.tapi.MPipe;
import com.metavize.mvvm.tapi.Pipeline;
import com.metavize.mvvm.tapi.PipelineFoundry;
import com.metavize.mvvm.tapi.SoloPipeSpec;
import com.metavize.mvvm.tran.PipelineEndpoints;
import com.metavize.mvvm.tran.PipelineStats;
import org.apache.log4j.Logger;

class PipelineFoundryImpl implements PipelineFoundry
{
    private static final PipelineFoundryImpl PIPELINE_FOUNDRY_IMPL
        = new PipelineFoundryImpl();

    private static final Logger eventLogger
        = MvvmContextImpl.context().eventLogger();
    private static final Logger logger
        = Logger.getLogger(PipelineFoundryImpl.class);

    private final Map<Fitting, List<MPipe>> incomingMPipes
        = new ConcurrentHashMap<Fitting, List<MPipe>>();
    private final Map<Fitting, List<MPipe>> outgoingMPipes
        = new ConcurrentHashMap<Fitting, List<MPipe>>();

    private final Map casings = new ConcurrentHashMap();

    private final Map<InetSocketAddress, Fitting> connectionFittings
        = new ConcurrentHashMap<InetSocketAddress, Fitting>();
    private final Map<Integer, PipelineImpl> pipelines
        = new ConcurrentHashMap<Integer, PipelineImpl>();

    private static final Map<Policy, Map<Fitting, List<MPipeFitting>>> incomingChains
        = new ConcurrentHashMap<Policy, Map<Fitting, List<MPipeFitting>>>();
    private static final Map<Policy, Map<Fitting, List<MPipeFitting>>> outgoingChains
        = new ConcurrentHashMap<Policy, Map<Fitting, List<MPipeFitting>>>();

    private PipelineFoundryImpl() { }

    public static PipelineFoundryImpl foundry()
    {
        return PIPELINE_FOUNDRY_IMPL;
    }

    public PipelineDesc weld(IPSessionDesc sd)
    {
        Long t0 = System.nanoTime();

        PolicyRule pr = selectPolicy(sd);
        if (pr == null) {
            logger.error("No policy rule found for session " + sd);
        }
        Policy p = null == pr ? null : pr.getPolicy();

        InetAddress sAddr = sd.serverAddr();
        int sPort = sd.serverPort();

        InetSocketAddress socketAddress = new InetSocketAddress(sAddr, sPort);
        Fitting start = connectionFittings.remove(socketAddress);

        if (SessionEndpoints.PROTO_TCP == sd.protocol()) {
            if (null == start) {
                switch (sPort) {
                case 21:
                    start = Fitting.FTP_CTL_STREAM;
                    break;

                case 25:
                    start = Fitting.SMTP_STREAM;
                    break;

                case 80:
                    start = Fitting.HTTP_STREAM;
                    break;

                case 110:
                    start = Fitting.POP_STREAM;
                    break;

                case 143:
                    start = Fitting.IMAP_STREAM;
                    break;

                default:
                    start = Fitting.OCTET_STREAM;
                    break;
                }
            }
        } else {
            start = Fitting.OCTET_STREAM; // XXX we should have UDP hier.
        }

        long ct0 = System.nanoTime();
        List<MPipeFitting> chain = makeChain(sd, p, start);
        long ct1 = System.nanoTime();

        long ft0 = System.nanoTime();
        List<ArgonAgent> agents = filter(chain, sd, pr);
        long ft1 = System.nanoTime();

        PipelineImpl pipeline = new PipelineImpl(sd.id(), chain);
        pipelines.put(sd.id(), pipeline);

        Long t1 = System.nanoTime();
        if (logger.isDebugEnabled()) {
            logger.debug("sid: " + sd.id() + " pipe in " + (t1 - t0)
                         + " made: " + (ct1 - ct0)
                         + " filtered: " + (ft1 - ft0)
                         + " nanos: " + chain);
        }

        return new PipelineDesc(pr, agents);
    }

    public void registerEndpoints(IPSessionDesc start, IPSessionDesc end)
    {
        eventLogger.info(new PipelineEndpoints(start, end));
    }

    public void destroy(IPSessionDesc start, IPSessionDesc end)
    {
        PipelineImpl pipeline = pipelines.remove(start.id());

        if (logger.isDebugEnabled()) {
            logger.debug("removed: " + pipeline + " for: " + start.id());
        }

        eventLogger.info(new PipelineStats(start, end));

        pipeline.destroy();
    }

    public void registerMPipe(MPipe mPipe)
    {
        synchronized (this) {
            registerMPipe(incomingMPipes, mPipe, new MPipeComparator(true));
            registerMPipe(outgoingMPipes, mPipe, new MPipeComparator(false));
        }
    }

    public void deregisterMPipe(MPipe mPipe)
    {
        synchronized (this) {
            deregisterMPipe(incomingMPipes, mPipe, new MPipeComparator(true));
            deregisterMPipe(outgoingMPipes, mPipe, new MPipeComparator(false));
        }
    }

    public void registerCasing(MPipe insideMPipe, MPipe outsideMPipe)
    {
       if (insideMPipe.getPipeSpec() != outsideMPipe.getPipeSpec()) {
            throw new IllegalArgumentException("casing constraint violated");
        }

       synchronized (this) {
           casings.put(insideMPipe, outsideMPipe);
           clearCache();
       }
    }

    public void deregisterCasing(MPipe insideMPipe)
    {
        synchronized (this) {
            casings.remove(insideMPipe);
            clearCache();
        }
    }

    public void registerConnection(InetSocketAddress socketAddress,
                                   Fitting fitting)
    {
        connectionFittings.put(socketAddress, fitting);
    }

    public Pipeline getPipeline(int sessionId)
    {
        return (Pipeline)pipelines.get(sessionId);
    }

    // private methods --------------------------------------------------------

    private List<MPipeFitting> makeChain(IPSessionDesc sd, Policy p,
                                         Fitting start)
    {
        boolean incoming = sd.clientIntf() == IntfConverter.OUTSIDE;

        Map<Policy, Map<Fitting, List<MPipeFitting>>> chains = incoming
            ? incomingChains : outgoingChains;

        List<MPipeFitting> mPipeFittings = null;

        Map<Fitting, List<MPipeFitting>> fcs = chains.get(p);

        if (null != fcs) {
            mPipeFittings = fcs.get(start);
        }

        if (null == mPipeFittings) {
            synchronized (this) {
                fcs = chains.get(p);

                if (null == fcs) {
                    fcs = new HashMap<Fitting, List<MPipeFitting>>();
                    chains.put(p, fcs);
                } else {
                    mPipeFittings = fcs.get(start);
                }

                if (null == mPipeFittings) {
                    mPipeFittings = new LinkedList<MPipeFitting>();

                    Map<MPipe, MPipe> availCasings = new HashMap(casings);

                    Map<Fitting, List<MPipe>> mp = incoming ? incomingMPipes
                        : outgoingMPipes;
                    Map<Fitting, List<MPipe>> availMPipes
                        = new HashMap<Fitting, List<MPipe>>(mp);

                    weld(mPipeFittings, start, p, availMPipes, availCasings);

                    fcs.put(start, mPipeFittings);
                }
            }
        }

        return mPipeFittings;
    }

    private List<ArgonAgent> filter(List<MPipeFitting> mPipeFittings,
                                    IPSessionDesc sd, PolicyRule pr)
    {
        List<ArgonAgent> l = new ArrayList<ArgonAgent>(mPipeFittings.size());

        MPipe end = null;

        for (Iterator<MPipeFitting> i = mPipeFittings.iterator(); i.hasNext(); ) {
            MPipeFitting mpf = i.next();

            if (null != end) {
                i.remove();
                if (mpf.mPipe == end) {
                    end = null;
                }
            } else {
                MPipe mPipe = mpf.mPipe;
                if (mPipe.getPipeSpec().matches(pr, sd)) {
                    l.add(mPipe.getArgonAgent());
                } else {
                    i.remove();
                    end = mpf.end;
                }
            }
        }

        return l;
    }

    private void weld(List<MPipeFitting> mPipeFittings, Fitting start,
                      Policy p, Map<Fitting, List<MPipe>> availMPipes,
                      Map<MPipe, MPipe> availCasings)
    {
        weldMPipes(mPipeFittings, start, p, availMPipes, availCasings);
        weldCasings(mPipeFittings, start, p, availMPipes, availCasings);
    }

    private void weldMPipes(List<MPipeFitting> mPipeFittings,
                            Fitting start, Policy p,
                            Map<Fitting, List<MPipe>> availMPipes,
                            Map<MPipe, MPipe> availCasings)
    {
        TRY_AGAIN:
        for (Iterator<Fitting> i = availMPipes.keySet().iterator(); i.hasNext(); ) {
            Fitting f = i.next();
            if (start.instanceOf(f)) {
                List l = availMPipes.get(f);
                i.remove();
                for (Iterator<MPipe> j = l.iterator(); j.hasNext(); ) {
                    MPipe mPipe = j.next();
                    if (null == mPipe) {
                        weldCasings(mPipeFittings, start, p, availMPipes,
                                    availCasings);
                    } else if (mPipe.getPipeSpec().matchesPolicy(p)) {
                        mPipeFittings.add(new MPipeFitting(mPipe, start));
                    }
                }
                break TRY_AGAIN;
            }
        }
    }

    private void weldCasings(List<MPipeFitting> mPipeFittings, Fitting start,
                             Policy p, Map<Fitting, List<MPipe>> availMPipes,
                             Map<MPipe, MPipe> availCasings)
    {
        TRY_AGAIN:
        for (Iterator<MPipe> i = availCasings.keySet().iterator(); i.hasNext(); ) {
            MPipe insideMPipe = i.next();
            CasingPipeSpec ps = (CasingPipeSpec)insideMPipe.getPipeSpec();
            Fitting f = ps.getInput();

            if (!ps.matchesPolicy(p)) {
                i.remove();
            } else if (start.instanceOf(f)) {
                MPipe outsideMPipe = availCasings.get(insideMPipe);
                i.remove();
                mPipeFittings.add(new MPipeFitting(insideMPipe, start,
                                                   outsideMPipe));
                CasingPipeSpec cps = (CasingPipeSpec)insideMPipe
                    .getPipeSpec();
                Fitting insideFitting = cps.getOutput();
                weldMPipes(mPipeFittings, insideFitting, p, availMPipes,
                           availCasings);
                mPipeFittings.add(new MPipeFitting(outsideMPipe, insideFitting));
                break TRY_AGAIN;
            }
        }
    }

    private void registerMPipe(Map mPipes, MPipe mPipe, Comparator c)
    {
        SoloPipeSpec sps = (SoloPipeSpec)mPipe.getPipeSpec();
        Fitting f = sps.getFitting();

        List l = (List)mPipes.get(f);

        if (null == l) {
            l = new ArrayList();
            l.add(null);
            mPipes.put(f, l);
        }

        int i = Collections.binarySearch(l, mPipe, c);
        l.add(0 > i ? -i - 1 : i, mPipe);

        clearCache();
    }

    private void deregisterMPipe(Map<Fitting, List<MPipe>> mPipes,
                                 MPipe mPipe, Comparator c)
    {
        SoloPipeSpec sps = (SoloPipeSpec)mPipe.getPipeSpec();
        Fitting f = sps.getFitting();

        List l = mPipes.get(f);

        int i = Collections.binarySearch(l, mPipe, c);
        if (0 > i) {
            logger.warn("deregistering nonregistered pipe");
        } else {
            l.remove(i);
        }

        clearCache();
    }

    private PolicyRule selectPolicy(IPSessionDesc sd)
    {
        PolicyManagerImpl pmi = PolicyManagerImpl.policyManager();
        UserPolicyRule[] userRules = pmi.userRules;
        SystemPolicyRule[] sysRules = pmi.sysRules;

        for (UserPolicyRule upr : userRules) {
            if (upr.matches(sd)) {
                return upr;
            }
        }

        for (SystemPolicyRule spr : sysRules) {
            if (spr.matches(sd)) {
                return spr;
            }
        }

        return null;
    }

    private void clearCache()
    {
        incomingChains.clear();
        outgoingChains.clear();
    }
}
