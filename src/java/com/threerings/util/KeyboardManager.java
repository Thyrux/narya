          //
// $Id: KeyboardManager.java,v 1.2 2001/12/12 18:09:20 shaper Exp $

package com.threerings.yohoho.puzzle.util;

import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Iterator;

import com.samskivert.swing.Controller;
import com.samskivert.util.HashIntMap;
import com.samskivert.util.Interval;
import com.samskivert.util.IntervalManager;

import com.threerings.yohoho.Log;

/**
 * The keyboard manager observes keyboard actions on a particular
 * component and posts commands associated with the key presses to the
 * {@link Controller} hierarchy.  It allows specifying the key repeat
 * rate, and will begin repeating a key immediately after it is held down
 * rather than depending on the system-specific key repeat delay/rate.
 */
public class KeyboardManager implements KeyListener
{
    /**
     * Constructs a keyboard manager.
     *
     * @param target the component whose keyboard events are to be observed.
     * @param xlate the key translator used to map keyboard events to
     * controller action commands.
    */
    public KeyboardManager (Component target, KeyTranslator xlate)
    {
        // save off references
        _target = target;
        _xlate = xlate;

        // listen to key events
        target.addKeyListener(this);

        // listen to focus events so that we can cease repeating if we
        // lose focus
        target.addFocusListener(new FocusAdapter() {
            public void focusLost (FocusEvent e) {
                releaseAllKeys();
            }
        });
    }

    /**
     * Sets whether the keyboard manager accepts keyboard input.
     */
    public void setEnabled (boolean enabled)
    {
        // release all keys if we were enabled and are soon to not be
        if (!enabled && _enabled) {
            releaseAllKeys();
        }
        _enabled = enabled;
    }

    /**
     * Sets the expected delay in milliseconds between each key
     * press/release event the keyboard manager should expect to receive
     * while a key is repeating.
     */
    public void setRepeatDelay (long delay)
    {
        _repeatDelay = delay;
    }

    /**
     * Sets the delay in milliseconds between each repeat key action
     * command posted by the keyboard manager while a key is down.
     */
    public void setPressDelay (long delay)
    {
        _pressDelay = delay;
    }

    /**
     * Releases all keys and ceases any hot repeating action that may be
     * going on.
     */
    public void releaseAllKeys ()
    {
        Iterator iter = _keys.elements();
        while (iter.hasNext()) {
            ((KeyInfo)iter.next()).release();
        }
    }

    // documentation inherited
    public void keyPressed (KeyEvent e)
    {
        logKey("keyPressed", e);

        // bail if we're not accepting input
        if (!_enabled) {
            return;
        }

        // get the action command associated with this key
        int keyCode = e.getKeyCode();
        if (_xlate.hasCommand(keyCode)) {
            // get the info object for this key, creating one if necessary
            KeyInfo info = (KeyInfo)_keys.get(keyCode);
            if (info == null) {
                info = new KeyInfo(keyCode);
                _keys.put(keyCode, info);
            }

            // remember the last time this key was pressed
            info.setPressTime(System.currentTimeMillis());
        }
    }

    // documentation inherited
    public void keyReleased (KeyEvent e)
    {
        logKey("keyReleased", e);

        // bail if we're not accepting input
        if (!_enabled) {
            return;
        }

        // get the info object for this key
        KeyInfo info = (KeyInfo)_keys.get(e.getKeyCode());
        if (info == null) {
            // Log.warning("Received key released event for a key that " +
            // "seems not to have been previously pressed " +
            // "[e=" + e + "].");
            return;
        }

        // remember the last time we received a key release
        info.setReleaseTime(System.currentTimeMillis());
    }

    // documentation inherited
    public void keyTyped (KeyEvent e)
    {
        // logKey("keyTyped", e);
    }

    /**
     * Logs the given message and key.
     */
    protected void logKey (String msg, KeyEvent e)
    {
        int keyCode = e.getKeyCode();
        Log.info(msg + " [key=" + KeyEvent.getKeyText(keyCode) + "].");
    }

    protected class KeyInfo implements Interval
    {
        /**
         * Constructs a key info object for the given key code.
         */
        public KeyInfo (int keyCode)
        {
            _keyCode = keyCode;
            _keyText = KeyEvent.getKeyText(_keyCode);
            _pressCommand = _xlate.getPressCommand(_keyCode);
            _releaseCommand = _xlate.getReleaseCommand(_keyCode);
        }

        /**
         * Sets the last time the key was pressed.
         */
        public synchronized void setPressTime (long time)
        {
            _lastPress = time;
            _lastRelease = time;

            if (_iid == -1) {
                // register an interval to post the command associated
                // with the key press until the key is decidedly released
                _iid = IntervalManager.register(this, _pressDelay, null, true);

                // post the initial key press command if applicable
                if (_pressCommand != null) {
                    Controller.postAction(_target, _pressCommand);
                }
            }
        }

        /**
         * Sets the last time the key was released.
         */
        public synchronized void setReleaseTime (long time)
        {
            _lastRelease = time;
        }

        /**
         * Releases the key if pressed and cancels any active key repeat
         * interval.
         */
        public synchronized void release ()
        {
            Log.info("Releasing key [key=" + _keyText + "].");

            // remove the sub-interval, if any
            if (_siid != -1) {
                IntervalManager.remove(_siid);
                _siid = -1;
            }

            // remove the repeat interval
            if (_iid != -1) {
                IntervalManager.remove(_iid);
                _iid = -1;
            }

            // post the key release command if applicable
            if (_releaseCommand != null) {
                Controller.postAction(_target, _releaseCommand);
            }
        }

        // documentation inherited
        public synchronized void intervalExpired (int id, Object arg)
        {
            long now = System.currentTimeMillis();
            long deltaRelease = now - _lastRelease;

            Log.info("intervalExpired [id=" + id +
                     ", key=" + _keyText +
                     ", deltaPress=" + (now - _lastPress) +
                     ", deltaRelease=" + deltaRelease + "].");

            if (id == _iid) {
                // handle a normal interval where we either (a) create a
                // sub-interval if we can't yet determine definitively
                // whether the key is still down, (b) cease repeating if
                // we're certain the key is now up, or (c) repeat the key
                // command if we're certain the key is still down
                if (_lastRelease != _lastPress) {
                    if (deltaRelease < _repeatDelay) {
                        // register a one-shot sub-interval to
                        // definitively check whether the key was released
                        Log.info("Registering sub-interval to check key " +
                                 "release.");
                        long delay = _repeatDelay - deltaRelease;
                        _siid = IntervalManager.register(
                            this, delay, null, false);
                             
                    } else {
                        // we know the key was released, so cease repeating
                        release();
                    }

                } else if (_pressCommand != null) {
                    // post the key press command again
                    Log.info("Repeating command [cmd=" + _pressCommand + "].");
                    Controller.postAction(_target, _pressCommand);
                }

            } else if (id == _siid) {
                // clear out the non-recurring sub-interval identifier
                _siid = -1;

                // provide the last word on whether the key was released
                if ((_lastRelease != _lastPress) &&
                    deltaRelease >= _repeatDelay) {
                    release();

                } else if (_pressCommand != null) {
                    // post the key command again
                    Log.info("Repeating command [cmd=" + _pressCommand + "].");
                    Controller.postAction(_target, _pressCommand);
                }
            }
        }

        /**
         * Returns a string representation of the key info object.
         */
        public String toString ()
        {
            return "[key=" + _keyText + "]";
        }

        /** The unique interval identifier for the sub-interval used to
         * handle the case where the main interval wakes up to repeat the
         * currently pressed key and the last key release event was
         * received more recently than the expected repeat delay. */
        protected int _siid = -1;

        /** The unique interval identifier for the key repeat interval. */
        protected int _iid = -1;

        /** The last time a key released event was received for this key. */
        protected long _lastRelease;

        /** The last time a key pressed event was received for this key. */
        protected long _lastPress;

        /** The press action command associated with this key. */
        protected String _pressCommand;

        /** The release action command associated with this key. */
        protected String _releaseCommand;

        /** A text representation of this key. */
        protected String _keyText;

        /** The key code associated with this key info object. */
        protected int _keyCode;
    }

    /** The default repeat delay. */
    protected static final long DEFAULT_REPEAT_DELAY = 50L;

    /** The default key press delay. */
    protected static final long DEFAULT_PRESS_DELAY = 200L;

    /** The expected approximate milliseconds between each key
     * release/press event while the key is being auto-repeated. */
    protected long _repeatDelay = DEFAULT_REPEAT_DELAY;

    /** The milliseconds to sleep between sending repeat key commands. */
    protected long _pressDelay = DEFAULT_PRESS_DELAY;

    /** A hashtable mapping key codes to {@link KeyInfo} objects. */
    protected HashIntMap _keys = new HashIntMap();

    /** Whether the keyboard manager is accepting keyboard input. */
    protected boolean _enabled = true;

    /** The component that receives keyboard events and that we associate
     * with posted controller commands. */
    protected Component _target;

    /** The translator that maps keyboard events to controller commands. */
    protected KeyTranslator _xlate;
}
