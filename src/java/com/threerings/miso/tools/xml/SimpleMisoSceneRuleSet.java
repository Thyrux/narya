//
// $Id: SimpleMisoSceneRuleSet.java,v 1.1 2003/02/12 07:21:50 mdb Exp $

package com.threerings.miso.tools.xml;

import java.util.ArrayList;
import org.xml.sax.Attributes;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.Rule;

import com.samskivert.xml.CallMethodSpecialRule;
import com.samskivert.xml.SetFieldRule;
import com.samskivert.xml.SetPropertyFieldsRule;

import com.threerings.tools.xml.NestableRuleSet;

import com.threerings.miso.data.ObjectInfo;
import com.threerings.miso.data.SimpleMisoSceneModel;

/**
 * Used to parse a {@link SimpleMisoSceneModel} from XML.
 */
public class SimpleMisoSceneRuleSet implements NestableRuleSet
{
    // documentation inherited from interface
    public String getOuterElement ()
    {
        return SimpleMisoSceneWriter.OUTER_ELEMENT;
    }

    // documentation inherited from interface
    public void addRuleInstances (String prefix, Digester dig)
    {
        // this creates the appropriate instance when we encounter our
        // prefix tag
        dig.addRule(prefix, new Rule(dig) {
            public void begin (Attributes attributes) throws Exception {
                digester.push(createMisoSceneModel());
            }
            public void end () throws Exception {
                digester.pop();
            }
        });

        // set up rules to parse and set our fields
        dig.addRule(prefix + "/width", new SetFieldRule(dig, "width"));
        dig.addRule(prefix + "/height", new SetFieldRule(dig, "height"));
        dig.addRule(prefix + "/viewwidth", new SetFieldRule(dig, "vwidth"));
        dig.addRule(prefix + "/viewheight", new SetFieldRule(dig, "vheight"));
        dig.addRule(prefix + "/base", new SetFieldRule(dig, "baseTileIds"));

        dig.addObjectCreate(prefix + "/objects", ArrayList.class.getName());
        dig.addObjectCreate(prefix + "/objects/object",
                            ObjectInfo.class.getName());
        dig.addSetNext(prefix + "/objects/object", "add",
                       Object.class.getName());

        dig.addRule(prefix + "/objects/object",
                    new SetPropertyFieldsRule(dig));

        dig.addRule(prefix + "/objects", new CallMethodSpecialRule(dig) {
            public void parseAndSet (String bodyText, Object target)
                throws Exception
            {
                ArrayList ilist = (ArrayList)target;
                ArrayList ulist = new ArrayList();
                SimpleMisoSceneModel model = (SimpleMisoSceneModel)
                    digester.peek(1);

                // filter interesting and uninteresting into two lists
                for (int ii = 0; ii < ilist.size(); ii++) {
                    ObjectInfo info = (ObjectInfo)ilist.get(ii);
                    if (!info.isInteresting()) {
                        ilist.remove(ii--);
                        ulist.add(info);
                    }
                }

                // now populate the model
                SimpleMisoSceneModel.populateObjects(model, ilist, ulist);
            }
        });
    }

    protected SimpleMisoSceneModel createMisoSceneModel ()
    {
        return new SimpleMisoSceneModel(0, 0, 0, 0);
    }
}
