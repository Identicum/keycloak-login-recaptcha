package com.identicum.keycloak.recaptcha;

import org.jboss.logging.Logger;

import java.util.TimerTask;

public class HttpStats extends TimerTask {

    public static final Integer TO_MILLISECONDS = 1000;

    private final Logger logger;
    private final RestHandler restHandler;

    public HttpStats(Logger logger, RestHandler restHandler){
        this.logger = logger;
        this.restHandler = restHandler;
    }

    @Override
    public void run() {
        logger.infov("HTTP pool stats: {0}", restHandler.getStats().toString());
    }
}
