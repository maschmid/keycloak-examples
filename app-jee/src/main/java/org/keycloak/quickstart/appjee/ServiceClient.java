/*
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.keycloak.quickstart.appjee;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.util.JsonSerialization;

/**
 * Client that calls the service.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2015 Red Hat Inc.
 */
public class ServiceClient {

    private static final String SERVICE_URI_INIT_PARAM_NAME = "serviceUrl";

    static class MessageBean {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

    }

    public static class Failure extends Exception {

        private int status;
        private String reason;

        public Failure(int status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public int getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }
    }

    private static String getServiceUrl(HttpServletRequest req, KeycloakSecurityContext session) {
        String uri = req.getServletContext().getInitParameter(SERVICE_URI_INIT_PARAM_NAME);
        if (uri != null && !uri.contains("localhost"))
        	return uri;

    	String ip = req.getLocalAddr();

        if (ip != null){
	        ip = "localhost";
	        try {
	        	ip = java.net.InetAddress.getLocalHost().getHostAddress();
	        } catch (Exception e){
	            e.printStackTrace();
	        }
        }
        
        return "http://" + ip + ":8080/service";
    }
    
    public static String callService(HttpServletRequest req, KeycloakSecurityContext session, String action) throws Failure {
        HttpClient client = null;
        try {
            client = new DefaultHttpClient();
            HttpGet get = new HttpGet(getServiceUrl(req, session) + "/" + action);
            if (session != null) {
                get.addHeader("Authorization", "Bearer " + session.getTokenString());
            }

            HttpResponse response = client.execute(get);

            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != 200) {
                throw new Failure(status.getStatusCode(), status.getReasonPhrase());
            }

            HttpEntity entity = response.getEntity();
            InputStream is = entity.getContent();
            try {
                MessageBean message = (MessageBean)JsonSerialization.readValue(is, MessageBean.class);
                return message.getMessage();
            } finally {
                is.close();
            }
        } catch (Failure f) {
            throw f;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
        	if (client != null)
             client.getConnectionManager().shutdown();
        }
    }
}
