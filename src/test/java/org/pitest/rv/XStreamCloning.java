package org.pitest.rv;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.CompactWriter;

import java.io.StringWriter;
import java.io.Writer;
import java.util.WeakHashMap;

import static org.pitest.util.Unchecked.translateCheckedException;

@Deprecated
public class XStreamCloning {

    private static final XStream                           XSTREAM_INSTANCE          = new XStream(
            new PitXmlDriver());
    private static final WeakHashMap<ClassLoader, XStream> CACHE                     = new WeakHashMap<>();


    public static Object cloneForLoader(final Object o, final ClassLoader loader) {
        try {
            final String xml = toXml(o);
            final XStream foreignXstream = getXStreamForLoader(loader);
            return foreignXstream.fromXML(xml);
        } catch (final Exception ex) {
            throw translateCheckedException(ex);
        }

    }

    private static XStream getXStreamForLoader(final ClassLoader loader) {
        XStream foreginXstream = CACHE.get(loader);
        if (foreginXstream == null) {
            foreginXstream = new XStream(new PitXmlDriver());
            foreginXstream.setClassLoader(loader);
            XStream.setupDefaultSecurity(foreginXstream);
            foreginXstream.allowTypesByWildcard(new String[] {"**"});
            // possible that more than one instance will be created
            // per loader, but probably better than synchronizing the whole method
            synchronized (CACHE) {
                CACHE.put(loader, foreginXstream);
            }
        }
        return foreginXstream;
    }

    public static String toXml(final Object o) {
        final Writer writer = new StringWriter();
        XSTREAM_INSTANCE.marshal(o, new CompactWriter(writer));

        return writer.toString();
    }
    }
