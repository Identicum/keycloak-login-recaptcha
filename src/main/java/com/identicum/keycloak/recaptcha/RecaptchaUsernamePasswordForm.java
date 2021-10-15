package com.identicum.keycloak.recaptcha;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.events.Details;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.util.JsonSerialization;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.*;

public class RecaptchaUsernamePasswordForm extends UsernamePasswordForm implements Authenticator {
	public static final String G_RECAPTCHA_RESPONSE = "g-recaptcha-response";
	public static final String SITE_KEY = "siteKey";
	public static final String SITE_SECRET = "secret";

	private static final Logger logger = Logger.getLogger(RecaptchaUsernamePasswordForm.class);

	private String siteKey;
	private final CloseableHttpClient httpClient;

	public RecaptchaUsernamePasswordForm(CloseableHttpClient httpClient){
		this.httpClient = httpClient;
	}

	@Override
	protected Response createLoginForm( LoginFormsProvider form ) {
		logger.infov("Creating login form");
		if(siteKey != null) {
			logger.debugv("For site key " + siteKey);
			form.setAttribute("recaptchaRequired", true);
			form.setAttribute("recaptchaSiteKey", siteKey);
		}
		return super.createLoginForm( form );
	}

	@Override
	public void authenticate(AuthenticationFlowContext context) {
		logger.infov("Starting authentication flow");
		context.getEvent().detail(Details.AUTH_METHOD, "auth_method");
		AuthenticatorConfigModel captchaConfig = context.getAuthenticatorConfig();
		LoginFormsProvider form = context.form();
		logger.infov("Verifying recaptcha configuration");
		String isRecaptchaConfAvailableMsg = "Recaptcha configuration is not available";

		if (captchaConfig != null && captchaConfig.getConfig() != null
				&& captchaConfig.getConfig().get(SITE_KEY) != null
				&& captchaConfig.getConfig().get(SITE_SECRET) != null) {
			isRecaptchaConfAvailableMsg = "Recaptcha configuration is available";
			siteKey = captchaConfig.getConfig().get(SITE_KEY);
			form.setAttribute("recaptchaRequired", true);
			form.setAttribute("recaptchaSiteKey", siteKey);
		}

		logger.infov(isRecaptchaConfAvailableMsg);
		logger.debugv("Calling authenticate method from parent class");
		super.authenticate(context);
	}

	@Override
	public void action(AuthenticationFlowContext context) {
		MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
		List<FormMessage> errors = new ArrayList<>();
		context.getEvent().detail(Details.AUTH_METHOD, "auth_method");
		String captcha = formData.getFirst(G_RECAPTCHA_RESPONSE);

		if (!Validation.isBlank(captcha)) {
			logger.debugv("Recaptcha response from form data: " + captcha);
			AuthenticatorConfigModel captchaConfig = context.getAuthenticatorConfig();
			String secret = captchaConfig.getConfig().get(SITE_SECRET);
			logger.infov("Validating recaptcha response");
			boolean success = validateRecaptcha(context, captcha, secret);
			if (!success) {
				errors.add(new FormMessage(null, Messages.RECAPTCHA_FAILED));
				logger.infov("Removing recaptcha response");
				formData.remove(G_RECAPTCHA_RESPONSE);
				return;
			}
		}
		logger.infov("Calling action method from parent class");
		super.action(context);
	}

	protected boolean validateRecaptcha(AuthenticationFlowContext context, String captcha, String secret) {
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

		logger.infov("Recaptcha validation successful");
		return success;
	}

}
