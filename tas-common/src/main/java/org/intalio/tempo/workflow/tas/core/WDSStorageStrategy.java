/**
 * Copyright (c) 2005-2006 Intalio inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intalio inc. - initial API and implementation
 */
package org.intalio.tempo.workflow.tas.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A WDS (Workflow Deployment Service) based {@link org.intalio.tempo.workflow.tas.core.StorageStrategy} implementation.
 * <p />
 * The URL's of stored attachment payloads will always have the following form: 
 * <pre> 
 * http://&lt;wds-endpoint&gt;/&lt;attachment-prefix&gt;/&lt;unique-attachment-URI&gt;
 * </pre>
 */
public class WDSStorageStrategy implements StorageStrategy {
    private static final Logger _logger = LoggerFactory.getLogger(WDSStorageStrategy.class);

    private static final String ATTACHMENT_URI_PREFIX = "attachments/";

    /**
     * WDS endpoint (including the trailing slash), such as <code>http://localhost:8080/wds/</code>
     */
    private String _wdsEndpoint;

    /**
     * Instance constructor.
     */
    public WDSStorageStrategy(String wdsEndpoint) {
        setEndpoint(wdsEndpoint);
    }

    public WDSStorageStrategy() {
    }

    /**
     * Populates an {@link HttpMethod} with necessary headers to create a valid WDS request.
     */
    private void setUpMethod(HttpMethod method) {
        method.addRequestHeader(new Header("Participant-Token", "")); // TODO: use a token here
    }

    public void setEndpoint(String endpoint) {
        if (endpoint == null) {
            throw new IllegalArgumentException("WDS endpoint may not be null");
        }
        _wdsEndpoint = endpoint;
    }

    public String storeAttachment(AttachmentMetadata metadata, InputStream payload) throws IOException {
        // need to sanitize the filename because of some browsers (e.g. Internet Exploder)
        String filename = sanitize(metadata.getFilename());
        String uri = java.util.UUID.randomUUID().toString() + "/" + filename;
        String fullUrl = _wdsEndpoint + ATTACHMENT_URI_PREFIX + uri;

        PutMethod putMethod = new PutMethod(fullUrl);
        setUpMethod(putMethod);
        putMethod.setRequestEntity(new InputStreamRequestEntity(payload));
        putMethod.setRequestHeader("Content-type", metadata.getMimeType());

        HttpClient httpClient = new HttpClient();
        int code = httpClient.executeMethod(putMethod);
        if (code != 200) {
            throw new RuntimeException("Error code: " + code);
        }
        _logger.debug("Stored attachment at: '" + fullUrl + "'");
        return fullUrl;
    }

    public void deleteAttachment(String url) throws UnavailableAttachmentException {
        _logger.debug("Requested to delete attachment: '" + url + "'");

        DeleteMethod deleteMethod = new DeleteMethod(url);
        setUpMethod(deleteMethod);
        HttpClient httpClient = new HttpClient();
        try {
            int code = httpClient.executeMethod(deleteMethod);
            if (code != 200) {
                throw new UnavailableAttachmentException("Error code: " + code + " when attempting to DELETE '" + url
                        + "'");
            }
        } catch (Exception e) {
            throw new UnavailableAttachmentException(e);
        }
        _logger.debug("Deleted attachment: '" + url + "'");
    }

    /**
     * Sanitize filename for inclusion into URL.
     * e.g.  C:\Foo -> Foo
     *       My Document.doc -> My+Document.doc
     */
    static String sanitize(String filename) {
        // find the local name: the last portion of the filename that does not contain ":", "/" or "\"
        Pattern regex = Pattern.compile("(.*?)([^:/\\\\]+)$");
        Matcher match = regex.matcher(filename);
        if (match.find()) {
            try {
                String localname = match.group(2);
                return URLEncoder.encode(localname, "UTF-8");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else throw new IllegalArgumentException("Invalid filename: " + filename);
    }
}
