/*
 * Copyright © YOLANDA. All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yolanda.nohttp;

import com.yolanda.nohttp.error.ClientError;
import com.yolanda.nohttp.error.NetworkError;
import com.yolanda.nohttp.error.ServerError;
import com.yolanda.nohttp.error.TimeoutError;
import com.yolanda.nohttp.error.URLError;
import com.yolanda.nohttp.error.UnKnownHostError;
import com.yolanda.nohttp.tools.HeaderParser;
import com.yolanda.nohttp.tools.NetUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.zip.GZIPInputStream;

/**
 * <p>Network operating interface, The implementation of the network layer</p>
 * Created in Jul 28, 2015 7:33:22 PM
 *
 * @author YOLANDA
 */
public final class HttpRestConnection extends BasicConnection implements ImplRestConnection {

    private static HttpRestConnection instance;

    public static HttpRestConnection getInstance() {
        if (instance == null)
            instance = new HttpRestConnection();
        return instance;
    }

    private HttpRestConnection() {
    }

    @Override
    public HttpResponse requestNetwork(ImplServerRequest request) {
        if (request == null)
            throw new IllegalArgumentException("request == null");

        Logger.d("--------------Request start--------------");

        Headers responseHeaders = new HttpHeaders();
        byte[] responseBody = null;
        Exception exception = null;

        HttpURLConnection httpConnection = null;
        try {
            if (!NetUtil.isNetworkAvailable(NoHttp.getContext()))
                throw new NetworkError("Network error");

            //MalformedURLException, IOException, ProtocolException, UnknownHostException, SocketTimeoutException
            httpConnection = getHttpConnection(request);
            Logger.d("-------Response start-------");
            int responseCode = httpConnection.getResponseCode();
            responseHeaders = parseResponseHeaders(new URI(request.url()), responseCode, httpConnection.getResponseMessage(), httpConnection.getHeaderFields());

            // handle body
            if (hasResponseBody(request.getRequestMethod(), responseCode)) {
                InputStream inputStream = null;
                try {
                    inputStream = httpConnection.getInputStream();
                    if (HeaderParser.isGzipContent(responseHeaders.getContentEncoding()))
                        inputStream = new GZIPInputStream(inputStream);
                    responseBody = readResponseBody(inputStream);
                } catch (IOException e) {
                    if (responseCode >= 500)
                        throw new ServerError("Internal Server Error: " + e.getMessage());
                    else if (responseCode >= 400)
                        throw new ClientError("Internal Client Error: " + e.getMessage());
                } finally {
                    if (inputStream != null)
                        inputStream.close();
                }
            }
        } catch (MalformedURLException e) {
            Logger.e(e);
            exception = new URLError(e.getMessage());
        } catch (UnknownHostException e) {
            Logger.e(e);
            exception = new UnKnownHostError(e.getMessage());
        } catch (SocketTimeoutException e) {
            Logger.e(e);
            exception = new TimeoutError(e.getMessage());
        } catch (Exception e) {
            Logger.e(e);
            exception = e;
        } finally {
            if (httpConnection != null)
                httpConnection.disconnect();
            Logger.d("-------Response end-------");
        }
        Logger.d("--------------Request finish--------------");
        return new HttpResponse(false, responseHeaders, responseBody, exception);
    }
}
