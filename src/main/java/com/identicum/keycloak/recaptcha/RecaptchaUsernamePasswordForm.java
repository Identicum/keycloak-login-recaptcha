package com.identicum.keycloak.recaptcha;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
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
import java.io.InputStream;
import java.util.*;

public class RecaptchaUsernamePasswordForm extends UsernamePasswordForm implements Authenticator{
	public static final String G_RECAPTCHA_RESPONSE = "g-recaptcha-response";
	public static final String SITE_KEY = "site.key";
	public static final String SITE_SECRET = "secret";
	private static final Logger logger = Logger.getLogger(RecaptchaUsernamePasswordForm.class);

	private String siteKey;

	@Override
	protected Response createLoginForm( LoginFormsProvider form ) {
		logger.info("Creating login form with recaptcha for site key " + siteKey);
		form.setAttribute("recaptchaRequired", true);
		form.setAttribute("recaptchaSiteKey", siteKey);
		return super.createLoginForm( form );
	}

	@Override
	public void authenticate(AuthenticationFlowContext context) {
		logger.info("Starting authentication flow");
		context.getEvent().detail(Details.AUTH_METHOD, "auth_method");
		AuthenticatorConfigModel captchaConfig = context.getAuthenticatorConfig();
		LoginFormsProvider form = context.form();
		logger.info("Verifying recaptcha configuration");
		if (captchaConfig == null || captchaConfig.getConfig() == null
				|| captchaConfig.getConfig().get(SITE_KEY) == null
				|| captchaConfig.getConfig().get(SITE_SECRET) == null) {
			form.addError(new FormMessage(null, Messages.RECAPTCHA_NOT_CONFIGURED));
			logger.error("Recaptcha configuration is not available");
			return;
		}
		logger.info("Recaptcha configuration is available");
		siteKey = captchaConfig.getConfig().get(SITE_KEY);
		form.setAttribute("recaptchaRequired", true);
		form.setAttribute("recaptchaSiteKey", siteKey);

		logger.debug("Calling authenticate method from parent class");
		super.authenticate(context);
	}

	@Override
	public void action(AuthenticationFlowContext context) {
		MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
		List<FormMessage> errors = new ArrayList<>();
		boolean success = false;
		context.getEvent().detail(Details.AUTH_METHOD, "auth_method");
		String captcha = formData.getFirst(G_RECAPTCHA_RESPONSE);
		logger.debug("Recaptcha response from form data: " + captcha);

		if (!Validation.isBlank(captcha)) {
			AuthenticatorConfigModel captchaConfig = context.getAuthenticatorConfig();
			String secret = captchaConfig.getConfig().get(SITE_SECRET);
			logger.info("Validating recaptcha response");
			success = validateRecaptcha(context, success, captcha, secret);
		}
		if (success) {
			logger.debug("Calling action method from parent class");
			super.action(context);
		} else {
			errors.add(new FormMessage(null, Messages.RECAPTCHA_FAILED));
			logger.info("Removing recaptcha response");
			formData.remove(G_RECAPTCHA_RESPONSE);
		}
	}

	protected boolean validateRecaptcha(AuthenticationFlowContext context, boolean success, String captcha, String secret) {
		HttpClient httpClient = HttpClients.createDefault();
		String uri = "https://www.google.com/recaptcha/api/siteverify";
		HttpPost post = new HttpPost(uri);
		List<NameValuePair> formparams = new LinkedList<>();
		formparams.add(new BasicNameValuePair("secret", secret));
		formparams.add(new BasicNameValuePair("response", captcha));
		formparams.add(new BasicNameValuePair("remoteip", context.getConnection().getRemoteAddr()));

		try {
			UrlEncodedFormEntity form = new UrlEncodedFormEntity(formparams, "UTF-8");
			post.setEntity(form);
			logger.info("Executing request to " + uri);
			logger.debug("Executing request with parameters response: " + captcha + " and remoteip: " + context.getConnection().getRemoteAddr());
			HttpResponse response = httpClient.execute(post);
			InputStream content = response.getEntity().getContent();
			try {
				Map json = JsonSerialization.readValue(content, Map.class);
				Object score = json.get("score");
				logger.debug("Score: " + score);
				Object val = json.get("success");
				success = Boolean.TRUE.equals(val);
			} finally {
				content.close();
			}
		} catch (Exception e) {
			logger.error("Recaptcha validation failed");
			ServicesLogger.LOGGER.recaptchaFailed(e);
		}
		logger.info("Recaptcha validation successful");
		return success;
	}

}
