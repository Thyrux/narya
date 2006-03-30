//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2004 Three Rings Design, Inc., All Rights Reserved
// http://www.threerings.net/code/narya/
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.presents.client;

import java.util.Arrays;

import com.threerings.presents.Log;
import com.threerings.presents.net.PingRequest;
import com.threerings.presents.net.PongResponse;

/**
 * Used to compute the client/server time delta, attempting to account for
 * the network delay experienced when the server sends its current time to
 * the client.
 */
public class DeltaCalculator
{
    /**
     * Constructs a delta calculator which is used to calculate the time
     * delta between the client and server, accounding reasonably well for
     * the delay introduced by sending a timestamp over the network from
     * the server to the client.
     */
    public DeltaCalculator ()
    {
        _deltas = new long[CLOCK_SYNC_PING_COUNT];
    }

    /**
     * Should we send another ping?
     */
    public boolean shouldSendPing ()
    {
        return (_ping == null) && !isDone();
    }

    /**
     * Must be called when a ping message is sent to the server.
     */
    public void sentPing (PingRequest ping)
    {
        _ping = ping;
    }

    /**
     * Must be called when the pong response arrives back from the server.
     *
     * @return true if we've iterated sufficiently many times to establish
     * a stable time delta estimate.
     */
    public boolean gotPong (PongResponse pong)
    {
        // don't freak out if they keep calling gotPong() after we're done
        if (_iter >= _deltas.length) {
            return true;
        }

        // make a note of when the ping message was sent and when the pong
        // response was received (both in client time)
        long send = _ping.getPackStamp(), recv = pong.getUnpackStamp();
        _ping = null; // clear out the saved sent ping

        // make a note of when the pong response was sent (in server time)
        // and the processing delay incurred on the server
        long server = pong.getPackStamp(), delay = pong.getProcessDelay();

        // compute the network delay (round-trip time divided by two)
        long nettime = (recv - send - delay)/2;

        // the time delta is the client time when the pong was received
        // minus the server's send time (plus network delay): dT = C - S
        _deltas[_iter] = recv - (server + nettime);

        Log.debug("Calculated delta [delay=" + delay +
                  ", nettime=" + nettime + ", delta=" + _deltas[_iter] +
                  ", rtt=" + (recv-send) + "].");

        return (++_iter >= CLOCK_SYNC_PING_COUNT);
    }

    /**
     * Returns the best estimate client/server time-delta.
     */
    public long getTimeDelta ()
    {
        if (_iter == 0) { // no responses yet
            return 0L;
        }

        if (true) {
            // return the mean
            long est = 0;
            for (int ii=0; ii < _iter; ii++) {
                est += _deltas[ii];
            }
            return (est / _iter);

        } else {
            // return the median value

            // copy the deltas array so that we don't alter things before
            // all pongs have arrived
            long[] deltasCopy = new long[_iter];
            System.arraycopy(_deltas, 0, deltasCopy, 0, _iter);

            // sort the estimates and return one from the middle
            Arrays.sort(deltasCopy);
            return deltasCopy[deltasCopy.length/2];
        }
    }

    /**
     * Returns true if this calculator has enough data to compute a time
     * delta estimate. Stick a fork in it!
     */
    public boolean isDone ()
    {
        return (_iter >= CLOCK_SYNC_PING_COUNT);
    }

    /** The number of ping/pong iterations we've made. */
    protected int _iter;

    /** Client/server time delta estimates. */
    protected long[] _deltas;

    /** A reference to the most recently sent ping which we use to obtain
     * the appropriate send stamp when we get the corresponding receive
     * stamp. */
    protected PingRequest _ping;

    /** The number of times we PING during clock sync to try to smooth out
     * network jiggling. */
    protected static final int CLOCK_SYNC_PING_COUNT = 3;
}
