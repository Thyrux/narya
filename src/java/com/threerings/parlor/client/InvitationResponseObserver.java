//
// $Id: InvitationResponseObserver.java,v 1.1 2001/10/01 02:56:35 mdb Exp $

package com.threerings.parlor.client;

/**
 * A client entity that wishes to generate invitations for games must
 * implement this interface. An invitation can be accepted, rejected or
 * countered. A countered invitation is one where the game configuration
 * is adjusted by the invited player and proposed back to the inviting
 * player.
 */
public interface InvitationObserver
{
    /**
     * Called if the invitation was accepted.
     */
    public void invitationAccepted (int inviteId);

    /**
     * Called if the invitation was rejected.
     *
     * @param message a message provided by the rejecting user explaining
     * the reason for their rejection, or the empty string if no message
     * was provided.
     */
    public void invitationRejected (int inviteId, String message);

    /**
     * Called if the invitation was countered with an alternate game
     * configuration.
     *
     * @param config the game configuration proposed by the invited
     * player.
     */
    public void invitationCountered (int inviteId, GameConfig config);
}
