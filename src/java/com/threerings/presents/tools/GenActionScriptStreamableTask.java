//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2010 Three Rings Design, Inc., All Rights Reserved
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

package com.threerings.presents.tools;

import java.lang.reflect.Field;

import java.util.List;
import java.util.Set;

import java.io.File;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;

public class GenActionScriptStreamableTask extends GenActionScriptTask
{
    @Override
    protected void convert (File javaSource, Class<?> sclass, File outputLocation)
        throws Exception
    {
        // Generate the current version of the streamable
        StreamableClassRequirements reqs = new StreamableClassRequirements(sclass);

        Set<String> imports = Sets.newLinkedHashSet();
        imports.add(ObjectInputStream.class.getName());
        imports.add(ObjectOutputStream.class.getName());
        String extendsName = "";
        if (!sclass.getSuperclass().equals(Object.class)) {
            extendsName = addImportAndGetShortType(sclass.getSuperclass(), false, imports);
        }

        Set<String> implemented = Sets.newLinkedHashSet();
        for (Class<?> iface : sclass.getInterfaces()) {
            implemented.add(addImportAndGetShortType(iface, false, imports));
        }
        List<ASField> fields = Lists.newArrayList();
        for (Field f : reqs.streamedFields) {
            fields.add(new ASField(f, imports));
        }
        String output = mergeTemplate("com/threerings/presents/tools/streamable_as.tmpl",
            "header", _header,
            "package", sclass.getPackage().getName(),
            "classname", sclass.getSimpleName(),
            "imports", imports,
            "extends", extendsName,
            "implements", Joiner.on(", ").join(implemented),
            "superclassStreamable", reqs.superclassStreamable,
            "fields", fields);

        if (outputLocation.exists()) {
            // Merge in the previously generated version
            String existing = Files.toString(outputLocation, Charsets.UTF_8);
            output = new GeneratedSourceMerger().merge(output, existing);
        } else if (!outputLocation.getParentFile().exists()) {
            // Make sure the directory exists before trying to write there
            Preconditions.checkArgument(outputLocation.getParentFile().mkdirs(),
                "Unable to create directory to write '%s'", outputLocation.getAbsolutePath());
        }
        Files.write(output, outputLocation, Charsets.UTF_8);
    }

    protected static class ASField
    {
        public final String name;
        public final String simpleType;
        public final String reader;
        public final String writer;

        public ASField (Field f, Set<String> imports)
        {
            this.name = f.getName();
            this.simpleType = addImportAndGetShortType(f.getType(), true, imports);

            // Reading and writing Lists uses ArrayStreamer.INSTANCE directly
            if (List.class.isAssignableFrom(f.getType())) {
                imports.add("com.threerings.io.streamers.ArrayStreamer");
            }
            this.reader = toReadObject(f.getType());
            this.writer = toWriteObject(f.getType(), name);
        }
    }
}
