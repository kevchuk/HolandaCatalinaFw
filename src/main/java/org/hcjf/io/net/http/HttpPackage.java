package org.hcjf.io.net.http;

import org.hcjf.layers.Layer;
import org.hcjf.layers.LayerInterface;
import org.hcjf.layers.Layers;
import org.hcjf.log.Log;
import org.hcjf.properties.SystemProperties;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * This class represents all the king of packages between server and
 * client side in a http communication.
 * @author javaito
 */
public abstract class HttpPackage {

    private static final byte LINE_SEPARATOR_CR = '\r';
    private static final byte LINE_SEPARATOR_LF = '\n';
    public static final String STRING_LINE_SEPARATOR = "\r\n";
    public static final String LINE_FIELD_SEPARATOR = " ";
    public static final String HTTP_FIELD_START = "?";
    public static final String HTTP_FIELD_SEPARATOR = "&";
    public static final String HTTP_FIELD_ASSIGNATION = "=";
    public static final String HTTP_CONTEXT_SEPARATOR = "/";

    static {
        Layers.publishLayer(ChunkedDecoderLayer.class);
    }

    private HttpProtocol protocol;
    private String httpVersion;
    private final Map<String, HttpHeader> headers;
    private byte[] body;

    //This fields are only for internal parsing.
    private List<String> lines;
    private boolean onBody;
    private boolean complete;
    private ByteArrayOutputStream currentBuffer;
    private TransferDecodingLayerInterface transferDecodingLayer;

    public HttpPackage() {
        this.httpVersion = HttpVersion.VERSION_1_1;
        this.headers = new HashMap<>();
        this.body = new byte[0];
        this.protocol = HttpProtocol.HTTP;
    }

    protected HttpPackage(HttpPackage httpPackage) {
        this.httpVersion = httpPackage.httpVersion;
        this.headers = httpPackage.headers;
        this.body = httpPackage.body;
        this.protocol = httpPackage.protocol;
    }

    /**
     * Return the http protocol.
     * @return Http protocol
     */
    public HttpProtocol getProtocol() {
        return protocol;
    }

    /**
     * Set the http protocol.
     * @param protocol Http protocol
     */
    public void setProtocol(HttpProtocol protocol) {
        this.protocol = protocol;
    }

    /**
     * Return the body of the package.
     * @return Body.
     */
    public final byte[] getBody() {
        return body;
    }

    /**
     * Set the body of the package.
     * @param body Body.
     */
    public final void setBody(byte[] body) {
        this.body = body;
    }

    /**
     * Return the version of the http protocol
     * @return Http protocol version.
     */
    public final String getHttpVersion() {
        return httpVersion;
    }

    /**
     * Set the version of the http protocol
     * @param httpVersion Version of the http protocol
     */
    public final void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    /**
     * Return true if the http package is complete.
     * @return Trus if the package is complete and false in the otherwise.
     */
    public final boolean isComplete() {
        return complete;
    }

    /**
     * Add a new header into a package.
     * @param header New header.
     */
    public final void addHeader(HttpHeader header) {
        if(header == null) {
            throw new NullPointerException("Null header");
        }
        headers.put(header.getHeaderName(), header);
    }

    /**
     * Return in a unmodificable list, all the headers contained
     * into the package.
     * @return List of the headers.
     */
    public final Collection<HttpHeader> getHeaders() {
        return Collections.unmodifiableCollection(headers.values());
    }

    /**
     * Return the header instance with the specific name.
     * @param headerName Name of the founded header.
     * @return Founded header or null if there are'nt any header with this name.
     */
    public final HttpHeader getHeader(String headerName) {
        HttpHeader result = null;
        for(String name : headers.keySet()) {
            if(name.equalsIgnoreCase(headerName)) {
                result = headers.get(name);
            }
        }
        return result;
    }

    /**
     * Verify if the package contains any header with the specific name.
     * @param headerName Finding header name.
     * @return Return true if there are any header with the specific name and false in the otherwise.
     */
    public final boolean containsHeader(String headerName) {
        return getHeader(headerName) != null;
    }

    /**
     * Add a portion of data into the package.
     * @param data Portion of data.
     */
    public final synchronized void addData(byte[] data) {
        if(!complete) {
            if (currentBuffer == null) {
                currentBuffer = new ByteArrayOutputStream();
                lines = new ArrayList<>();
                onBody = false;
                complete = false;
            }

            if (onBody) {
                writeBody(data);
            } else {
                String line;
                for (int i = 0; i < data.length - 1; i++) {
                    if (data[i] == LINE_SEPARATOR_CR && data[i + 1] == LINE_SEPARATOR_LF) {
                        if (currentBuffer.size() == 0) {

                            for(int j = 1; j < lines.size(); j++) {
                                addHeader(new HttpHeader(lines.get(j)));
                            }

                            HttpHeader transferEncodingHeader = getHeader(HttpHeader.TRANSFER_ENCODING);
                            if(transferEncodingHeader != null) {
                                try {
                                    transferDecodingLayer = Layers.get(TransferDecodingLayerInterface.class, transferEncodingHeader.getHeaderValue());
                                } catch (Exception ex) {
                                    Log.w(SystemProperties.get(SystemProperties.Net.Http.LOG_TAG),
                                            "Transfer decoding layer not found", ex);
                                }
                            }

                            //The previous line is empty
                            //Start body, because there are two CRLF together
                            currentBuffer.reset();
                            writeBody(data, i + 2, data.length - (i + 2));
                            onBody = true;
                            break;
                        } else {
                            //The current body is a new line
                            line = new String(currentBuffer.toByteArray()).trim();
                            if(!line.isEmpty()) {
                                lines.add(line);
                            }
                            currentBuffer.reset();
                            i++;
                        }
                    } else {
                        currentBuffer.write(data[i]);
                    }
                }
            }

            if (onBody) {
                if (bodyDone()) {
                    setBody(getAccumulatedBody());
                    processFirstLine(lines.get(0));
                    processBody(getBody());
                    currentBuffer = null;
                    complete = true;
                }
            }
        } else {
            Log.d(SystemProperties.get(SystemProperties.Net.Http.LOG_TAG), "Trying to add data into a complete http package.");
        }
    }

    /**
     * This method store the fragment information into the specific
     * decoder implementation
     * @param data Fragment information.
     */
    private void writeBody(byte[] data) {
        writeBody(data, 0, data.length);
    }

    /**
     * This method store the fragment information into the specific
     * decoder implementation
     * @param data Fragment information.
     * @param off Start index into the array.
     * @param len End index into the array.
     */
    private void writeBody(byte[] data, int off, int len) {
        if(transferDecodingLayer == null) {
            currentBuffer.write(data, off, len);
        } else {
            transferDecodingLayer.add(ByteBuffer.wrap(data, off, len));
        }
    }

    /**
     * Returns the accumulated body into the specific decode implementation.
     * @return Accumulated body.
     */
    private byte[] getAccumulatedBody() {
        byte[] result;
        if(transferDecodingLayer == null) {
            result = currentBuffer.toByteArray();
        } else {
            result = transferDecodingLayer.getBody();
        }
        return result;
    }

    /**
     * Verify if the body of the package is complete.
     * @return Return true if the body is complete or false in the otherwise
     */
    protected boolean bodyDone() {
        boolean result;
        if(transferDecodingLayer == null) {
            int length = 0;
            HttpHeader contentLengthHeader = getHeader(HttpHeader.CONTENT_LENGTH);
            if (contentLengthHeader != null) {
                length = Integer.parseInt(contentLengthHeader.getHeaderValue().trim());
            }
            result = currentBuffer.size() >= length;
        } else {
            result = transferDecodingLayer.done(this);
        }
        return result;
    }

    /**
     * Return the body trimmed.
     * @param body Raw body.
     * @return Trimmed body
     */
    protected byte[] trimBody(byte[] body) {
        return body;
    }

    /**
     * This method must be implemented to process the body information.
     * @param body Body information.
     */
    protected abstract void processBody(byte[] body);

    /**
     * This method must be implemented to process the first line of the package.
     * @param firstLine First line of the package.
     */
    protected abstract void processFirstLine(String firstLine);

    /**
     * Return the bytes that represent the string of the protocol name.
     * @return Protocol name bytes.
     */
    public abstract byte[] getProtocolHeader();

    /**
     * Enum with the http protocols
     */
    public enum HttpProtocol {

        HTTP,

        HTTPS

    }

    /**
     * Specify the interface for all the implementations of http body decode method.
     */
    public interface TransferDecodingLayerInterface extends LayerInterface {

        /**
         * Add a new fragment for the current body.
         * @param bodyFragment Body fragment.
         */
        void add(ByteBuffer bodyFragment);

        /**
         * Verify if the body is done depends of the decode method.
         * @param httpPackage Package to verify if the body is complete.
         * @return Body done.
         */
        boolean done(HttpPackage httpPackage);

        /**
         * Returns the decoded instance of the body.
         * @return Body.
         */
        byte[] getBody();

    }

    /**
     * This decoder implementation resolve the chunked encoding method for http.
     */
    public static class ChunkedDecoderLayer extends Layer implements TransferDecodingLayerInterface {

        private static final int DEFAULT_SIZE = -1;
        private static final byte SLASH_R_BYTE = '\r';

        private int fragmentSize;
        private int byteWritten;
        private final ByteArrayOutputStream lengthBuffer;
        private final ByteArrayOutputStream bodyBuffer;
        private boolean done;

        public ChunkedDecoderLayer() {
            super(HttpHeader.CHUNKED, false);
            fragmentSize = DEFAULT_SIZE;
            byteWritten = 0;
            bodyBuffer = new ByteArrayOutputStream();
            lengthBuffer = new ByteArrayOutputStream();
            done = false;
        }

        /**
         * Adds a fragment of the body into the internal buffer.
         * @param bodyFragment Body fragment.
         */
        @Override
        public void add(ByteBuffer bodyFragment) {
            byte currentByte;
            while(bodyFragment.position() < bodyFragment.limit()) {
                if(fragmentSize == DEFAULT_SIZE) {
                    currentByte = bodyFragment.get();
                    if(currentByte == SLASH_R_BYTE) {
                        fragmentSize = Integer.parseInt(lengthBuffer.toString(), 16);
                        lengthBuffer.reset();
                        byteWritten = DEFAULT_SIZE;
                        if(fragmentSize == 0) {
                            done = true;
                            break;
                        }
                    } else {
                        lengthBuffer.write(currentByte);
                    }
                } else {
                    if(byteWritten == DEFAULT_SIZE) {
                        bodyFragment.get(); //Discards the '\n' byte
                        byteWritten++;
                    } else if(byteWritten == fragmentSize) {
                        bodyFragment.get(); //Discards the '\r' byte
                        bodyFragment.get(); //Discards the '\n' byte
                        fragmentSize = DEFAULT_SIZE;
                    } else {
                        bodyBuffer.write(bodyFragment.get());
                        byteWritten++;
                    }
                }
            }
        }

        /**
         * Verify if the body is complete. The body is complete when the
         * chunked size into the fragment is zero.
         * * @param httpPackage Package to verify if the body is complete.
         * For this particular implementation this param is unuseful.
         * @return True if the body is complete and false in the otherwise
         */
        @Override
        public boolean done(HttpPackage httpPackage) {
            return done;
        }

        /**
         * Returns the accumulated body, if the body is complete then this accumulated body is
         * the complete body.
         * @return Accumulated body.
         */
        @Override
        public byte[] getBody() {
            return bodyBuffer.toByteArray();
        }

    }
}
