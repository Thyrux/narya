package com.threerings.presents.dobj {

import flash.events.EventDispatcher;

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.io.Streamable;

public class DObject extends EventDispatcher
    implements Streamable
{
    public function getOid ():int
    {
        return _oid;
    }

    public function postEvent (DEvent event) :void
    {

    }

    // documentation inherited from interface Streamable
    public function writeObject (out :ObjectOutputStream) :void
    {
        out.writeInt(_oid);
    }

    // documentation inherited from interface Streamable
    public function readObject (ins :ObjectInputStream) :void
    {
        _oid = ins.readInt();
    }

    protected var _oid :int;
}
}
