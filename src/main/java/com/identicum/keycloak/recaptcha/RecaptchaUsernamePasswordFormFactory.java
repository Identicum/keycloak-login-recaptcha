package com.identicum.keycloak.recaptcha;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
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

public class RecaptchaUsernamePasswordFormFactory implements AuthenticatorFactory, DisplayTypeAuthenticatorFactory {

    public static final String PROVIDER_ID = "recaptcha-u-p-form";
    public static final String MAX_HTTP_CONNECTIONS = "maxHttpConnections";
    public static final String API_SOCKET_TIMEOUT = "apiSocketTimeout";
    public static final String API_CONNECT_TIMEOUT = "apiConnectTimeout";
    public static final String API_CONNECTION_REQUEST_TIMEOUT = "apiConnectionRequestTimeout";
    private static final Integer MAX_HTTP_CONNECTIONS_VALUE = 5;
    private static final Integer API_SOCKET_TIMEOUT_VALUE = 1000;
    private static final Integer API_CONNECT_TIMEOUT_VALUE = 1000;
    private static final Integer API_CONNECTION_REQUEST_TIMEOUT_VALUE = 1000;

    private static final Logger logger = Logger.getLogger(RecaptchaUsernamePasswordFormFactory.class);

    private CloseableHttpClient httpClient;
    private List<ProviderConfigProperty> lastConfiguration = null;

    @Override
    public Authenticator create(KeycloakSession session) {

        if(this.httpClient == null || !getConfigProperties().equals(lastConfiguration)){
            logger.infov("Loading properties");
            this.lastConfiguration = getConfigProperties();
        }
        else {
            logger.debugv("HttpClient already instantiated");
        }
        return new RecaptchaUsernamePasswordForm(this.httpClient);
    }

    @Override
    public Authenticator createDisplay(KeycloakSession session, String displayType) {
        if (displayType == null) return new RecaptchaUsernamePasswordForm(this.httpClient);
        if (!OAuth2Constants.DISPLAY_CONSOLE.equalsIgnoreCase(displayType)) return null;
        return ConsoleUsernamePasswordAuthenticator.SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        logger.infov("Initializing recaptcha username password form factory version: " + getClass().getPackage().getImplementationVersion());

        int maxConnections = config.getInt(MAX_HTTP_CONNECTIONS, MAX_HTTP_CONNECTIONS_VALUE);
        int socketTimeout = config.getInt(API_SOCKET_TIMEOUT, API_SOCKET_TIMEOUT_VALUE);
        int connectTimeout = config.getInt(API_CONNECT_TIMEOUT, API_CONNECT_TIMEOUT_VALUE);
        int connectionRequestTimeout = config.getInt(API_CONNECTION_REQUEST_TIMEOUT, API_CONNECTION_REQUEST_TIMEOUT_VALUE);
        logger.infov("Initializing HTTP pool with maxConnections: {0}, connectionRequestTimeout: {1}, connectTimeout: {2}, socketTimeout: {3}", maxConnections, connectionRequestTimeout, connectTimeout, socketTimeout);
        PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();
        poolingHttpClientConnectionManager.setMaxTotal(maxConnections);
        poolingHttpClientConnectionManager.setDefaultMaxPerRoute(maxConnections);
        poolingHttpClientConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
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

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
        if( this.httpClient != null) {
            try {
                this.httpClient.close();
            } catch (IOException e) {
                logger.warn("Error closing http response", e);
            }
        }
    }

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
        property.setDefaultValue(MAX_HTTP_CONNECTIONS_VALUE);
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(API_SOCKET_TIMEOUT);
        property.setLabel("API Socket Timeout");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue(API_SOCKET_TIMEOUT_VALUE);
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(API_CONNECT_TIMEOUT);
        property.setLabel("API Connect Timeout");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue(API_CONNECT_TIMEOUT_VALUE);
        CONFIG_PROPERTIES.add(property);

        property = new ProviderConfigProperty();
        property.setName(API_CONNECTION_REQUEST_TIMEOUT);
        property.setLabel("API Connection Request Timeout");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue(API_CONNECTION_REQUEST_TIMEOUT_VALUE);
        CONFIG_PROPERTIES.add(property);
    }

	@Override
	public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
	}

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

}