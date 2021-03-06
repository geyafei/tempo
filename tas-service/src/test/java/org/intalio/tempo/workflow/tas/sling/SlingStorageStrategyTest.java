package org.intalio.tempo.workflow.tas.sling;

import junit.framework.TestCase;

import org.intalio.tempo.security.Property;
import org.intalio.tempo.workflow.tas.core.AttachmentMetadata;
import org.intalio.tempo.workflow.tas.core.AuthCredentials;
import org.intalio.tempo.workflow.tas.core.AuthStrategy;
import org.intalio.tempo.workflow.tas.core.TaskAttachmentService;
import org.intalio.tempo.workflow.tas.core.TaskAttachmentServiceImpl;
import org.intalio.tempo.workflow.tas.sling.SlingStorageStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple test case to test attachments in Sling.
 * This requires a running server.
 */
public class SlingStorageStrategyTest extends TestCase {

    private static final Logger _logger = LoggerFactory.getLogger(SlingStorageStrategyTest.class);

    private TaskAttachmentService _service;
    private AuthCredentials _credentials = new AuthCredentials("token");

    class DummyAuthStrategy implements AuthStrategy {
        public Property[] authenticate(AuthCredentials credentials) {
            _logger.debug("Dummy authorization OK.");
            _logger.debug(credentials.toString());
            return null;
        }
    }

    @Override
    protected void setUp() throws Exception {
        SlingStorageStrategy ass = new SlingStorageStrategy();
        _service = new TaskAttachmentServiceImpl(new DummyAuthStrategy(), ass);

    }

    /**
     * Stores a simple text file 
     * @throws Exception
     */
    public void testStorage() throws Exception {
        AttachmentMetadata metadata = new AttachmentMetadata();
        metadata.setMimeType("text/plain");
        String string = "hello.txt";
        metadata.setFilename(string);
        String url = _service.add(_credentials, metadata, "Hello world!".getBytes("UTF-8"));
        System.out.println("URL: " + url);
        _service.delete(_credentials, url);
    }

    /**
     * Stores an xml file
     * @throws Exception
     */
    public void testStoreXmlFile() throws Exception {
        AttachmentMetadata metadata = new AttachmentMetadata();
        metadata.setMimeType("text/xml");
        metadata.setFilename("log4j.xml");
        _service.add(_credentials, metadata, this.getClass().getResource("/log4j.xml").toExternalForm());
    }
}
