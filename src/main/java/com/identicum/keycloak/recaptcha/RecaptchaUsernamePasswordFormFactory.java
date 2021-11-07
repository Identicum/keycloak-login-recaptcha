package com.identicum.keycloak.recaptcha;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.DisplayTypeAuthenticatorFactory;
import org.keycloak.authentication.authenticators.console.ConsoleUsernamePasswordAuthenticator;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.provider.ProviderConfigProperty;

import static com.identicum.keycloak.recaptcha.HttpStats.TO_MILLISECONDS;
import static com.identicum.keycloak.recaptcha.RestConfiguration.*;

public class RecaptchaUsernamePasswordFormFactory implements AuthenticatorFactory, DisplayTypeAuthenticatorFactory {

    private static final String PROVIDER_ID = "recaptcha-u-p-form";
    private static final Logger logger = Logger.getLogger(RecaptchaUsernamePasswordFormFactory.class);

    private static Map<String, String> lastConfiguration;
    private static Timer httpStats;

    protected static RestHandler restHandler;

    @Override
    public Authenticator create(KeycloakSession session) {
        return new RecaptchaUsernamePasswordForm();
    }

    @Override
    public Authenticator createDisplay(KeycloakSession session, String displayType) {
        if (displayType == null) return new RecaptchaUsernamePasswordForm();
        if (!OAuth2Constants.DISPLAY_CONSOLE.equalsIgnoreCase(displayType)) return null;
        return ConsoleUsernamePasswordAuthenticator.SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        logger.infov("Initializing recaptcha username password form factory version: " + getClass().getPackage().getImplementationVersion());
        httpStats = new Timer();
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getReferenceCategory() {
        return PasswordCredentialModel.TYPE;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }
    
    public static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED
    };

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public String getDisplayType() {
        return "Recaptcha Username Password Form";
    }

    @Override
    public String getHelpText() {
        return "Validates a username and password from login form + google recaptcha";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

	private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(RecaptchaUsernamePasswordForm.SITE_KEY);
        property.setLabel("Recaptcha Site Key");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Google Recaptcha Site Key");
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(RecaptchaUsernamePasswordForm.SITE_SECRET);
        property.setLabel("Recaptcha Secret");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Google Recaptcha Secret");
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(MAX_HTTP_CONNECTIONS);
        property.setLabel("Max pool connections");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Max http connections in pool");
        property.setDefaultValue(5);
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(API_SOCKET_TIMEOUT);
        property.setLabel("API Socket Timeout");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Max time [milliseconds] to wait for response");
        property.setDefaultValue(1000);
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(API_CONNECT_TIMEOUT);
        property.setLabel("API Connect Timeout");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Max time [milliseconds] to establish the connection");
        property.setDefaultValue(1000);
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(API_CONNECTION_REQUEST_TIMEOUT);
        property.setLabel("API Connection Request Timeout");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("Max time [milliseconds] to wait until a connection in the pool is assigned to the requesting thread");
        property.setDefaultValue(1000);
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(HTTP_STATS_INTERVAL);
        property.setLabel("HttpStats interval");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setHelpText("How often [seconds] will the HTTP connection pool stats be displayed. 0 means disabled.");
        property.setDefaultValue(0);
        CONFIG_PROPERTIES.add(property);
    }

    private static void setHttpStats(Logger logger, RestHandler restHandler, Integer httpStatsInterval){
        httpStats.cancel();
        if(httpStatsInterval > 0){
            httpStats = new Timer();
            httpStats.schedule(new HttpStats(logger, restHandler), 0, httpStatsInterval * TO_MILLISECONDS);
        }
    }

    protected static void createRestHandlerAndSetStats(Map<String, String> configuration){
        if(restHandler == null || configuration == null || !configuration.equals(lastConfiguration)){
            logger.infov("Creating a new instance of restHandler");
            RestConfiguration restConfiguration = new RestConfiguration(configuration);
            restHandler = new RestHandler(restConfiguration);
            setHttpStats(logger, restHandler, restConfiguration.getHttpStatsInterval());
            lastConfiguration = configuration;
        }
        else {
            logger.infov("RestHandler already instantiated");
        }
    }

}