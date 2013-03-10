package org.sakaiproject.search.producer;

import org.sakaiproject.search.api.EntityContentProducer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Helper which generates mock of EntityContentProducers.
 *
 * @author Colin Hebert
 */
public final class ProducersHelper {
    private static final Random random = new Random();

    private ProducersHelper() {
    }

    public static EntityContentProducer getBasicContentProducer(String reference) {
        EntityContentProducer entityContentProducer = mock(EntityContentProducer.class);
        initialiseMockContentProducer(entityContentProducer, reference);
        return entityContentProducer;
    }

    public static EntityContentProducer getStringContentProducer(String reference) {
        String content = reference + " content";

        EntityContentProducer mockEntityContentProducer = getBasicContentProducer(reference);
        when(mockEntityContentProducer.isContentFromReader(reference)).thenReturn(false);
        when(mockEntityContentProducer.getContent(reference)).thenReturn(content);

        return mockEntityContentProducer;
    }

    public static EntityContentProducer getReaderContentProducer(String reference) {
        Reader content = new StringReader(reference + " content");

        EntityContentProducer mockEntityContentProducer = getBasicContentProducer(reference);
        when(mockEntityContentProducer.isContentFromReader(reference)).thenReturn(true);
        when(mockEntityContentProducer.getContentReader(reference)).thenReturn(content);

        return mockEntityContentProducer;
    }

    public static BinaryEntityContentProducer getBinaryContentProducer(String reference) {
        InputStream content = new ByteArrayInputStream((reference + " content").getBytes());

        BinaryEntityContentProducer mockEntityContentProducer = mock(BinaryContentHostingContentProducer.class);
        initialiseMockContentProducer(mockEntityContentProducer, reference);
        when(mockEntityContentProducer.getContentStream(reference)).thenReturn(content);

        return mockEntityContentProducer;
    }

    private static void initialiseMockContentProducer(EntityContentProducer mockContentProducer, String reference) {
        String container = reference + " container";
        String type = reference + " type";
        String title = reference + " title";
        String tool = reference + " tool";
        String url = reference + " url";
        String siteId = reference + " siteId";
        Map<String, Object> customProperties = new HashMap<String, Object>();
        customProperties.put(reference + "property1",
                Arrays.asList(reference + " property1 value1", reference + " property1 value2"));
        customProperties.put(reference + "property2", reference + " property2 value1");
        customProperties.put(reference + "property3", random.nextInt());
        customProperties.put(reference + "property4",
                new String[]{reference + " property4 value1", reference + " property4 value2"});


        when(mockContentProducer.matches(reference)).thenReturn(true);
        when(mockContentProducer.isForIndex(reference)).thenReturn(true);
        when(mockContentProducer.getContainer(reference)).thenReturn(container);
        when(mockContentProducer.getType(reference)).thenReturn(type);
        when(mockContentProducer.getTitle(reference)).thenReturn(title);
        when(mockContentProducer.getTool()).thenReturn(tool);
        when(mockContentProducer.getUrl(reference)).thenReturn(url);
        when(mockContentProducer.getSiteId(reference)).thenReturn(siteId);
        when(mockContentProducer.getCustomProperties(reference)).thenReturn((Map) customProperties);
    }

}
