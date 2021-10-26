# keycloak-login-recaptcha

Keycloak authenticator SPI using Google recaptcha v3.
Based on https://github.com/raptor-group/keycloak-login-recaptcha

## Compile module
```sh
mvn clean install
```

## Run project
```sh
docker-compose up
```

## Configure
- Register a site in https://www.google.com/recaptcha/admin
  - Type: v3
  - Domains: localhost
- Login to the admin console at http://localhost:8080/auth/admin/
  - username: admin
  - password: admin
- In the `recaptcha` realm -> Authentication -> Flows -> drop-down `Recaptcha` -> in the execution select `Actions` -> Config
  - enter the site key and secret you got in the registration process

## Test
- Navigate to http://localhost:8080/auth/realms/demorealm/account
- Select `Sign In`
- Register a new user
- Sign Out
- Sign In again, as the newly registered user

## How it works
Class  `RecaptchaUsernamePasswordForm` sets the form parameter `recaptchaRequired` to `true`.
Block in ./login.ftl under `<#if recaptchaRequired??>` adds the necessary frontend pieces.

## Realm configuration
A realm is automatically imported to simplify testing. This realm has the following configuration:
- Realm Settings -> Security Defenses -> Headers
  - X-Frame-Options -> `ALLOW-FROM https://www.google.com`
  - Content-Security-Policy -> `frame-src 'self' https://www.google.com; frame-ancestors 'self'; object-src 'none';`
- Authentication -> Flows
  - New Authentication Flow `Recaptcha` with a single execution `Recaptcha Username Password Form`
   execution was creted
- Authentication -> Flows -> Bindings
  - Browser Flow: `recaptcha`

## Troubleshooting
- Keycloak log should detail module activity, configured in ./startup-scripts/custom.cli
