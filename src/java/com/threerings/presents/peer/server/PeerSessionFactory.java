//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2007 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.presents.peer.server;

import com.threerings.util.Name;

import com.threerings.presents.net.AuthRequest;
import com.threerings.presents.peer.net.PeerCreds;
import com.threerings.presents.server.SessionFactory;
import com.threerings.presents.server.ClientResolver;
import com.threerings.presents.server.PresentsSession;

/**
 * Handles resolution of peer servers and passes non-peer resolution requests through to a normal
 * factory.
 */
public class PeerSessionFactory implements SessionFactory
{
    public PeerSessionFactory (SessionFactory delegate)
    {
        _delegate = delegate;
    }

    // documentation inherited from interface SessionFactory
    public Class<? extends PresentsSession> getSessionClass (AuthRequest areq)
    {
        if (areq.getCredentials() instanceof PeerCreds) {
            return PeerSession.class;
        } else {
            return _delegate.getSessionClass(areq);
        }
    }

    // documentation inherited from interface SessionFactory
    public Class<? extends ClientResolver> getClientResolverClass (Name username)
    {
        if (username.toString().startsWith(PeerCreds.PEER_PREFIX)) {
            return PeerClientResolver.class;
        } else {
            return _delegate.getClientResolverClass(username);
        }
    }

    protected SessionFactory _delegate;
}