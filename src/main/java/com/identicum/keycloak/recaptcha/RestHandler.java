package com.identicum.keycloak.recaptcha;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.pool.PoolStats;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.services.ServicesLogger;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.*;

import static org.jboss.logging.Logger.getLogger;

public class RestHandler {

	private static final Logger logger = getLogger(RestHandler.class);
	protected CloseableHttpClient httpClient;

	private final PoolingHttpClientConnectionManager poolingHttpClientConnectionManager;

	public RestHandler(RestConfiguration configuration) {
		Integer maxConnections = configuration.getMaxConnections();
		Integer socketTimeout = configuration.getApiSocketTimeout();
		Integer connectTimeout = configuration.getApiConnectTimeout();
		Integer connectionRequestTimeout = configuration.getApiConnectionRequestTimeout();
		logger.infov("Initializing HTTP pool with maxConnections: {0}, connectionRequestTimeout: {1}, connectTimeout: {2}, socketTimeout: {3}", maxConnections, connectionRequestTimeout, connectTimeout, socketTimeout);
		this.poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
		this.poolingHttpClientConnectionManager.setMaxTotal(maxConnections);
		this.poolingHttpClientConnectionManager.setDefaultMaxPerRoute(maxConnections);
		this.poolingHttpClientConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
			.setSoTimeout(socketTimeout)
			.build());
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(connectTimeout)
			.setConnectionRequestTimeout(connectionRequestTimeout)
			.build();
		this.httpClient = HttpClients.custom()
			.setDefaultRequestConfig(requestConfig)
			.setConnectionManager(poolingHttpClientConnectionManager)
			.build();
	}

	public boolean validateRecaptcha(AuthenticationFlowContext context, String captcha, String secret) {
		boolean success = false;
		String uri = "https://www.google.com/recaptcha/api/siteverify";
		HttpPost post = new HttpPost(uri);
		List<NameValuePair> formparams = new LinkedList<>();
		formparams.add(new BasicNameValuePair("secret", secret));
		formparams.add(new BasicNameValuePair("response", captcha));
		formparams.add(new BasicNameValuePair("remoteip", context.getConnection().getRemoteAddr()));

		try {
			UrlEncodedFormEntity form = new UrlEncodedFormEntity(formparams, "UTF-8");
			post.setEntity(form);
			logger.infov("Executing request to " + uri);
			logger.debugv("Executing request with parameters response: " + captcha + " and remoteip: " + context.getConnection().getRemoteAddr());
			HttpResponse response = this.httpClient.execute(post);
			InputStream content = response.getEntity().getContent();
			try {
				Map json = JsonSerialization.readValue(content, Map.class);
				Object score = json.get("score");
				logger.debugv("Score: " + score);
				Object val = json.get("success");
				success = Boolean.TRUE.equals(val);
				logger.infov("Recaptcha validation successful");
			} finally {
				content.close();
			}
		}
		catch(ConnectionPoolTimeoutException cpte) {
			logger.warnv("Connection pool timeout on recaptcha validation: {0}", cpte);
			success = true;
		}
		catch(ConnectTimeoutException cte) {
			logger.warnv("Connect timeout on recaptcha validation: {0}", cte);
			success = true;
		}
		catch(SocketTimeoutException ste) {
			logger.warnv("Socket timeout on recaptcha validation: {0}", ste);
			success = true;
		}
		catch(IOException io) {
			logger.error("Recaptcha validation failed");
			ServicesLogger.LOGGER.recaptchaFailed(io);
		}
		return success;
	}

	public Map<String, Integer> getStats() {
		HashMap<String, Integer> stats = new HashMap<>();
		PoolStats poolStats = poolingHttpClientConnectionManager.getTotalStats();
		stats.put("maxConnections", poolStats.getMax());
		stats.put("defaultMaxPerRoute", poolingHttpClientConnectionManager.getDefaultMaxPerRoute());
		stats.put("availableConnections", poolStats.getAvailable());
		stats.put("leasedConnections", poolStats.getLeased());
		stats.put("pendingConnections", poolStats.getPending());
		return stats;
	}

}