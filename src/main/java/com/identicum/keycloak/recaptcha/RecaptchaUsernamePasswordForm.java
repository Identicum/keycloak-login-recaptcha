package com.identicum.keycloak.recaptcha;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.events.Details;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.*;

public class RecaptchaUsernamePasswordForm extends UsernamePasswordForm implements Authenticator {
	public static final String G_RECAPTCHA_RESPONSE = "g-recaptcha-response";
	public static final String SITE_KEY = "siteKey";
	public static final String SITE_SECRET = "secret";

	private static final Logger logger = Logger.getLogger(RecaptchaUsernamePasswordForm.class);

	private String siteKey;

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
		Map<String, String> authConfig = context.getAuthenticatorConfig() == null ? null : context.getAuthenticatorConfig().getConfig();
		RecaptchaUsernamePasswordFormFactory.createRestHandlerAndSetStats(authConfig);

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
		logger.debugv("Recaptcha response from form data: " + captcha);

		if (!Validation.isBlank(captcha)) {
			AuthenticatorConfigModel captchaConfig = context.getAuthenticatorConfig();
			String secret = captchaConfig.getConfig().get(SITE_SECRET);
			logger.infov("Validating recaptcha response");
			boolean success = RecaptchaUsernamePasswordFormFactory.restHandler.validateRecaptcha(context, captcha, secret);
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

}
