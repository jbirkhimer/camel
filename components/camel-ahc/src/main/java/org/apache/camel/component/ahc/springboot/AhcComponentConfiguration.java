/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.ahc.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.ning.http.client.AsyncHttpClient;
import org.apache.camel.component.ahc.AhcBinding;
import com.ning.http.client.AsyncHttpClientConfig;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * To call external HTTP services using Async Http Client.
 */
@ConfigurationProperties(prefix = "camel.component.ahc")
public class AhcComponentConfiguration {

	/**
	 * To use a custom AsyncHttpClient
	 */
	private AsyncHttpClient client;
	/**
	 * To use a custom AhcBinding which allows to control how to bind between
	 * AHC and Camel.
	 */
	private AhcBinding binding;
	/**
	 * To configure the AsyncHttpClient to use a custom
	 * com.ning.http.client.AsyncHttpClientConfig instance.
	 */
	private AsyncHttpClientConfig clientConfig;
	/**
	 * Reference to a org.apache.camel.util.jsse.SSLContextParameters in the
	 * Registry. Note that configuring this option will override any SSL/TLS
	 * configuration options provided through the clientConfig option at the
	 * endpoint or component level.
	 */
	private SSLContextParameters sslContextParameters;
	/**
	 * Whether to allow java serialization when a request uses
	 * context-type=application/x-java-serialized-object This is by default
	 * turned off. If you enable this then be aware that Java will deserialize
	 * the incoming data from the request to Java and that can be a potential
	 * security risk.
	 */
	private boolean allowJavaSerializedObject;
	/**
	 * To use a custom HeaderFilterStrategy to filter header to and from Camel
	 * message.
	 */
	private HeaderFilterStrategy headerFilterStrategy;

	public AsyncHttpClient getClient() {
		return client;
	}

	public void setClient(AsyncHttpClient client) {
		this.client = client;
	}

	public AhcBinding getBinding() {
		return binding;
	}

	public void setBinding(AhcBinding binding) {
		this.binding = binding;
	}

	public AsyncHttpClientConfig getClientConfig() {
		return clientConfig;
	}

	public void setClientConfig(AsyncHttpClientConfig clientConfig) {
		this.clientConfig = clientConfig;
	}

	public SSLContextParameters getSslContextParameters() {
		return sslContextParameters;
	}

	public void setSslContextParameters(
			SSLContextParameters sslContextParameters) {
		this.sslContextParameters = sslContextParameters;
	}

	public boolean isAllowJavaSerializedObject() {
		return allowJavaSerializedObject;
	}

	public void setAllowJavaSerializedObject(boolean allowJavaSerializedObject) {
		this.allowJavaSerializedObject = allowJavaSerializedObject;
	}

	public HeaderFilterStrategy getHeaderFilterStrategy() {
		return headerFilterStrategy;
	}

	public void setHeaderFilterStrategy(
			HeaderFilterStrategy headerFilterStrategy) {
		this.headerFilterStrategy = headerFilterStrategy;
	}
}