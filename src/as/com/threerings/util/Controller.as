package com.threerings.util {

import flash.events.IEventDispatcher;

import com.threerings.mx.events.CommandEvent;

public class Controller
{
    /**
     * Set the panel being controlled.
     */
    protected function setControlledPanel (panel :IEventDispatcher) :void
    {
        if (_panel != null) {
            _panel.removeEventListener(
                CommandEvent.TYPE, handleCommandEvent);
        }
        _panel = panel;
        if (_panel != null) {
            _panel.addEventListener(
                CommandEvent.TYPE, handleCommandEvent);
        }
    }

    /**
     * Handle an action that was generated by our panel or some child.
     *
     * @return true if the specified action was handled, false otherwise.
     *
     * When creating your own controller, override this function and return
     * true for any command handled, and call super for any unknown commands.
     */
    public function handleAction (cmd :String, arg :Object) :Boolean
    {
        // fall back to a method named the cmd
        try {
            var fn :Function = (this["handle" + cmd] as Function);
            if (fn != null) {
                try {
                    fn(arg);
                } catch (e :Error) {
                    Log.getLog(this).warning("Error handling controller " +
                        "command [error=" + e + ", cmd=" + cmd +
                        ", arg=" + arg + "].");
                }
                // we "handled" the event, even if it threw an error
                return true;
            }
        } catch (e :Error) {
            // suppress, and fall through
        }

        // TODO: This warning should really be inside the CommandEvent
        // somewhere, and only generated if the event never gets cancelled
        Log.getLog(this).warning("Unhandled controller command " +
            "[cmd=" + cmd + ", arg=" + arg + "].");
        return false; // not handled
    }

    /**
     * Private function to handle the controller event and call
     * handleAction.
     */
    private function handleCommandEvent (event :CommandEvent) :void
    {
        if (handleAction(event.command, event.arg)) {
            // if we handle the event, stop it from moving outward to another
            // controller
            event.stopImmediatePropagation();
        }
    }

    /** The panel currently being controlled. */
    private var _panel :IEventDispatcher; 
}
}
